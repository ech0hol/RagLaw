DELETE FROM raglaw_category WHERE id IN (
    'cat_l3_case_civil_labor',
    'cat_l3_statute_civil_labor',
    'cat_l2_case_civil',
    'cat_l2_statute_civil',
    'cat_l1_case',
    'cat_l1_statute'
);

INSERT INTO raglaw_category (id, parent_id, level, code, name, path, doc_type, sort_order, enabled, created_at) VALUES
('cat_l1_statute', NULL, 1, 'STATUTE', 'Statute', '/STATUTE', 'STATUTE', 1, 1, CURRENT_TIMESTAMP),
('cat_l2_statute_civil', 'cat_l1_statute', 2, 'STATUTE_CIVIL', 'Civil Statute', '/STATUTE/CIVIL', 'STATUTE', 2, 1, CURRENT_TIMESTAMP),
('cat_l3_statute_civil_labor', 'cat_l2_statute_civil', 3, 'STATUTE_CIVIL_LABOR', 'Labor Statute', '/STATUTE/CIVIL/LABOR', 'STATUTE', 1, 1, CURRENT_TIMESTAMP),
('cat_l1_case', NULL, 1, 'CASE', 'Case', '/CASE', 'CASE', 2, 1, CURRENT_TIMESTAMP),
('cat_l2_case_civil', 'cat_l1_case', 2, 'CASE_CIVIL', 'Civil Case', '/CASE/CIVIL', 'CASE', 2, 1, CURRENT_TIMESTAMP),
('cat_l3_case_civil_labor', 'cat_l2_case_civil', 3, 'CASE_CIVIL_LABOR', 'Labor Case', '/CASE/CIVIL/LABOR', 'CASE', 1, 1, CURRENT_TIMESTAMP);
