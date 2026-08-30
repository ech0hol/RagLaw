INSERT INTO raglaw_category (id, parent_id, level, code, name, path, doc_type, sort_order, enabled)
SELECT 'cat_l3_statute_social_general', 'cat_l2_statute_social', 3, 'STATUTE_SOCIAL_GENERAL_SEED', '社会法综合', '/STATUTE/SOCIAL/GENERAL', 'STATUTE', 1, 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM raglaw_category WHERE path = '/STATUTE/SOCIAL/GENERAL' AND level = 3
);
