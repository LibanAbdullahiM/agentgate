-- 1. The audit trail is append-only in fact, not just by comment: any UPDATE, DELETE or TRUNCATE is refused
--    by the database, whatever code (or person) tries it. To reset a demo, drop the Postgres volume instead.
create or replace function audit_event_immutable() returns trigger as $$
begin
    raise exception 'audit_event is append-only: % is not allowed', tg_op;
end;
$$ language plpgsql;

create trigger audit_event_no_update_delete
    before update or delete on audit_event
    for each row execute function audit_event_immutable();

create trigger audit_event_no_truncate
    before truncate on audit_event
    for each statement execute function audit_event_immutable();

-- 2. Terminal products: Valio does not know their stock. false = "quantity not confirmed by Valio".
--    Existing quotes default to true. Not mapped by the Quote entity yet (added together with the T7 rule).
alter table quote add column stock_confirmed boolean not null default true;

-- 3. Valio's allowedLotSize (order increment). Not mapped by the Product entity yet.
alter table product add column lot_size numeric(12,3);

-- 4. Real vendor names can be longer than 120 characters; one long name would otherwise abort the whole import.
alter table product alter column supplier type varchar(255);
