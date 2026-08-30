INSERT INTO raglaw_category (id, parent_id, level, code, name, path, doc_type, sort_order, enabled)
SELECT 'cat_l3_statute_criminal_general', 'cat_l2_statute_criminal', 3, 'STATUTE_CRIMINAL_GENERAL', '刑法综合', '/STATUTE/CRIMINAL/GENERAL', 'STATUTE', 1, 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM raglaw_category WHERE path = '/STATUTE/CRIMINAL/GENERAL' AND level = 3
);

INSERT INTO raglaw_category (id, parent_id, level, code, name, path, doc_type, sort_order, enabled)
SELECT 'cat_l3_statute_admin_general', 'cat_l2_statute_admin', 3, 'STATUTE_ADMIN_GENERAL', '行政法综合', '/STATUTE/ADMIN/GENERAL', 'STATUTE', 1, 1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM raglaw_category WHERE path = '/STATUTE/ADMIN/GENERAL' AND level = 3
);
