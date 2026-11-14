-- T7: allergens the buyer excludes (GS1 codes, comma separated) for an agent's mandate.
alter table mandate add column avoid_allergens text;

-- T8: approval workflow. Machine-readable reasons for the inbox, and who decided what and when.
alter table purchase_order add column decision_codes   text;
alter table purchase_order add column decided_comment  text;
alter table purchase_order add column decided_at       timestamptz;

-- Search in every language (Finnish, Swedish, English names, brand, vendor), not just the English name.
-- Filled by the catalog import: run POST /admin/catalog/import again after this migration.
alter table product add column search_text text;

-- The 12 demo products are replaced by the real catalog (see V2). Re-activate them by hand if you need them.
update product set active = false where supplier = 'DEMO';
