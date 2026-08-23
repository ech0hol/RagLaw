INSERT INTO raglaw_category (id, parent_id, level, code, name, path, doc_type, sort_order, enabled, created_at) VALUES
('cat_l1_statute', NULL, 1, 'STATUTE', '法规', '/STATUTE', 'STATUTE', 1, 1, CURRENT_TIMESTAMP),
('cat_l2_statute_civil', 'cat_l1_statute', 2, 'STATUTE_CIVIL', '民法商法', '/STATUTE/CIVIL', 'STATUTE', 2, 1, CURRENT_TIMESTAMP),
('cat_l3_statute_civil_labor', 'cat_l2_statute_civil', 3, 'STATUTE_CIVIL_LABOR', '劳动合同法规', '/STATUTE/CIVIL/LABOR', 'STATUTE', 1, 1, CURRENT_TIMESTAMP);
