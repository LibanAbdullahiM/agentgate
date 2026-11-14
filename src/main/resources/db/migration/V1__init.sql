create table app_user (
    id            uuid primary key,
    username      varchar(64)  not null unique,
    password_hash varchar(100) not null,
    role          varchar(20)  not null,
    display_name  varchar(100),
    created_at    timestamptz  not null default now()
);

-- What an agent is allowed to do on behalf of a buyer.
create table mandate (
    id                  uuid primary key,
    agent_user_id       uuid          not null references app_user (id),
    buyer_name          varchar(120)  not null,
    max_order_value     numeric(12,2) not null,
    allow_substitutions boolean       not null default false,
    allowed_categories  text,
    expires_at          timestamptz,
    active              boolean       not null default true
);
create index idx_mandate_agent on mandate (agent_user_id);

-- Local copy of Valio's product data (imported from the challenge JSON).
create table product (
    code            varchar(40)  primary key,
    name            varchar(255) not null,
    category        varchar(120),
    unit            varchar(20)  not null default 'pcs',
    allergens       text,
    allergens_known boolean      not null default false,
    supplier        varchar(120),
    active          boolean      not null default true
);

-- A signed, fixed-price offer with an expiry.
create table quote (
    id                uuid primary key,
    agent_user_id     uuid          not null references app_user (id),
    product_code      varchar(40)   not null references product (code),
    quantity          numeric(12,3) not null,
    unit_price        numeric(12,4) not null,
    currency          varchar(3)    not null default 'EUR',
    line_total        numeric(12,2) not null,
    needed_by         date          not null,
    available_qty     numeric(12,3) not null,
    fully_available   boolean       not null,
    earliest_delivery date,
    created_at        timestamptz   not null,
    expires_at        timestamptz   not null,
    signature         varchar(128)  not null
);
create index idx_quote_agent on quote (agent_user_id);

create table purchase_order (
    id              uuid primary key,
    quote_id        uuid          not null unique references quote (id),
    agent_user_id   uuid          not null references app_user (id),
    idempotency_key varchar(100)  not null,
    status          varchar(24)   not null,
    total           numeric(12,2) not null,
    decision_reason text,
    decided_by      uuid references app_user (id),
    created_at      timestamptz   not null,
    updated_at      timestamptz   not null,
    unique (agent_user_id, idempotency_key)
);
create index idx_order_status on purchase_order (status);

-- Append-only: rows are only ever inserted.
create table audit_event (
    id          bigserial primary key,
    occurred_at timestamptz  not null default now(),
    actor_id    uuid,
    actor_role  varchar(20),
    action      varchar(60)  not null,
    entity_type varchar(40)  not null,
    entity_id   varchar(64)  not null,
    decision    varchar(30),
    reason      text,
    details     text
);
create index idx_audit_entity on audit_event (entity_type, entity_id);
