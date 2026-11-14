# AgentGate

**A trusted trade gateway for autonomous purchasing agents** (Junction 2026, Valio Aimo "Trusted Trade Agents").

An external buyer agent (for example a hotel chain's) can search a wholesaler's catalog, get a **signed fixed-price quote**,
and place an order. What the agent may decide alone is defined by a **mandate**; everything above it goes to a human.
Every step is written to an **append-only audit trail**. Business rules are deterministic code, not LLM output.

## Run it

```bash
cp .env.example .env
docker compose up --build
```


`VALIO_MODE=mock` (default) uses built-in deterministic prices and stock, so everything works offline.
Switch to `live` once the adapter matches Valio's API (see "Next steps").

Demo users (password = `SEED_PASSWORD`, default `demo1234`): `buyer-admin`, `approver`, `agent-1`.
The agent's mandate: max **500 EUR** per order, all categories.

## Try the flow

```bash
BASE=http://localhost:8080/api/v1

TOKEN=$(curl -s -X POST $BASE/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"agent-1","password":"demo1234"}' | jq -r .accessToken)

# 1. search
curl -s "$BASE/products?q=milk" -H "Authorization: Bearer $TOKEN" | jq

# 2. signed quote
QUOTE=$(curl -s -X POST $BASE/quotes -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"productCode":"DEMO-1001","quantity":20,"neededBy":"2026-11-20"}')
echo $QUOTE | jq
QID=$(echo $QUOTE | jq -r .id)

# 3. order (retry-safe)
curl -s -X POST $BASE/orders -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" -d "{\"quoteId\":\"$QID\"}" | jq
```

A small order is `CONFIRMED` automatically. Raise the quantity so the total exceeds 500 EUR and the order becomes
`PENDING_APPROVAL`. `GET /orders/{id}/trace` shows the full history in plain language.

Errors always look like `{ "code": "QUOTE_EXPIRED", "message": "...", "timestamp": "...", "details": [] }`.
Order states: `PENDING_APPROVAL -> APPROVED | REJECTED | EXPIRED`, `APPROVED -> CONFIRMED`.

## Trust protocol

Each quote includes `signature` (HMAC-SHA256) and `signedPayload` (the exact text that was signed: id, agent, product,
quantity, price, currency, total, delivery date, expiry). Changing any of those invalidates the signature, and
`POST /orders` refuses the quote (`QUOTE_SIGNATURE_INVALID`, logged).

## Project layout

```
src/main/java/fi/agentgate/
  security/   JWT login, filter, roles          quote/    signed quotes
  mandate/    what an agent may do              order/    order state machine
  catalog/    products + import                 policy/   guardrails (deterministic)
  valio/      Valio adapter (mock + live)       audit/    append-only event log
```
