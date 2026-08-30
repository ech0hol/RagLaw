INSERT INTO raglaw_category (id, parent_id, level, code, name, path, doc_type, sort_order, enabled) VALUES
('cat_l3_statute_social_general', 'cat_l2_statute_social', 3, 'STATUTE_SOCIAL_GENERAL', '社会法综合', '/STATUTE/SOCIAL/GENERAL', 'STATUTE', 1, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name);
