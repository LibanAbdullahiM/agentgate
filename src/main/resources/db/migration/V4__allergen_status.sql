-- Valio's allergen data has three levels per allergen code (GS1 codes such as AE, BM):
-- CONTAINS (stored in product.allergens), plus the two columns added here.
-- A code that appears in none of the three lists is UNKNOWN, never "safe".
alter table product add column allergens_may_contain text;
alter table product add column allergens_free_from  text;
