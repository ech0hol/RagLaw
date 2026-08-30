-- Enable all STATUTE/CASE L2 categories (national legal department taxonomy from V2).

UPDATE raglaw_category
SET enabled = 1
WHERE level = 2 AND doc_type IN ('STATUTE', 'CASE');

UPDATE raglaw_category
SET enabled = 1
WHERE id IN ('cat_l1_statute', 'cat_l1_case');
