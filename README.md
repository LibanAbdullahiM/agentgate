# AgentGate

**A trusted trade gateway for autonomous purchasing agents** (Junction 2026, Valio Aimo "Trusted Trade Agents").

An external buyer agent (for example a hotel chain's) can search a wholesaler's catalog, get a **signed fixed-price quote**,
and place an order. What the agent may decide alone is defined by a **mandate**; everything above it goes to a human.
Every step is written to an **append-only audit trail**. Business rules are deterministic code, not LLM output.

## Run it

```bash
cp .env.example .env          # set JWT_SECRET and QUOTE_SIGNING_SECRET (32+ chars each)
docker compose up --build     # Postgres + API on http://localhost:8080
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON (give this to the frontend dev): http://localhost:8080/v3/api-docs
- Without Docker: start Postgres, export the variables from `.env`, then `mvn spring-boot:run`.

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

## API (v1)

| Method | Path | Who | Purpose |
|---|---|---|---|
| POST | `/auth/login` | anyone | get a JWT |
| GET | `/products?q=&category=&page=&size=` | any user | search catalog |
| GET | `/products/{code}` | any user | product detail incl. allergens (`allergensKnown=false` means "no data") |
| GET | `/mandates/me` | AGENT | the agent's own limits |
| POST | `/quotes` | AGENT | price + availability + signed quote with expiry |
| GET | `/quotes/{id}` | owner / staff | read a quote |
| POST | `/orders` (header `Idempotency-Key`) | AGENT | order from a quote |
| GET | `/orders?status=` | AGENT (own) / staff (all) | list orders |
| GET | `/orders/{id}` | owner / staff | order detail |
| GET | `/orders/{id}/trace` | owner / staff | audit trail |
| POST | `/admin/catalog/import` | BUYER_ADMIN | import Valio product JSON from `./data/products.json` |

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

## Status

- [x] T0-T2 scaffold, Docker Compose, Flyway, Swagger, Valio adapter (mock + live skeleton)
- [x] T3 JWT auth, roles, mandates
- [x] T4 catalog search
- [x] T5 signed quotes
- [x] T6 order state machine with idempotency
- [ ] T7 full guardrail engine (substitutions, allergens, new product rules)
- [ ] T8 approval endpoints (approve / reject) - **needed for `PENDING_APPROVAL` orders to move on**
- [ ] T9 richer audit explanations
- [ ] T10-T13 partial availability, substitution ranking, allergen-safe mode, delivery/carbon comparison

## Next steps for the Valio data (do this first)

1. Put the downloaded product JSON at `data/products.json`, then call `POST /admin/catalog/import` as `buyer-admin`.
   Adjust the candidate field names in `catalog/ProductImportService.map()` to match Valio's JSON schema.
2. In Valio's Swagger, find the pricing and availability endpoints. Set `VALIO_PRICING_PATH` /
   `VALIO_AVAILABILITY_PATH` in `.env` and adjust field names in `valio/HttpValioGateway.java`.
3. Set `VALIO_API_KEY` in `.env` and `VALIO_MODE=live`. **Never commit `.env` or the API key.**

The challenge data is only for use inside the challenge, so `data/` is git-ignored.
