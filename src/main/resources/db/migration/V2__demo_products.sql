-- Demo products so the API works before the real Valio dataset is imported.
-- The real import (POST /api/v1/admin/catalog/import) upserts by product code.
insert into product (code, name, category, unit, allergens, allergens_known, supplier) values
 ('DEMO-1001', 'Lactose-free milk 1 L',        'Dairy',     'l',   'milk',        true,  'DEMO'),
 ('DEMO-1002', 'Oat drink barista 1 L',        'Dairy alternatives', 'l', 'oats',  true,  'DEMO'),
 ('DEMO-1003', 'Whole milk 1 L',               'Dairy',     'l',   'milk',        true,  'DEMO'),
 ('DEMO-1004', 'Butter 500 g',                 'Dairy',     'pcs', 'milk',        true,  'DEMO'),
 ('DEMO-1005', 'Natural yoghurt 1 kg',         'Dairy',     'pcs', 'milk',        true,  'DEMO'),
 ('DEMO-2001', 'Wheat flour 10 kg',            'Dry goods', 'pcs', 'gluten',      true,  'DEMO'),
 ('DEMO-2002', 'Rolled oats 5 kg',             'Dry goods', 'pcs', 'oats',        true,  'DEMO'),
 ('DEMO-2003', 'Rapeseed oil 5 l',             'Dry goods', 'pcs', '',            true,  'DEMO'),
 ('DEMO-3001', 'Potatoes 10 kg',               'Produce',   'pcs', null,          false, 'DEMO'),
 ('DEMO-3002', 'Carrots 5 kg',                 'Produce',   'pcs', null,          false, 'DEMO'),
 ('DEMO-4001', 'Chicken breast fillet 2 kg',   'Meat',      'pcs', '',            true,  'DEMO'),
 ('DEMO-5001', 'Rye bread 800 g',              'Bakery',    'pcs', 'gluten',      true,  'DEMO');
