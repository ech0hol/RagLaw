-- L3 categories for evaluation fixtures (ASCII names to avoid encoding issues in init scripts)

INSERT INTO raglaw_category (id, parent_id, level, code, name, path, doc_type, sort_order, enabled) VALUES
('cat_l3_statute_civil_labor', 'cat_l2_statute_civil', 3, 'STATUTE_CIVIL_LABOR', 'Labor Contract Statute', '/STATUTE/CIVIL/LABOR', 'STATUTE', 1, 1),
('cat_l3_statute_civil_contract', 'cat_l2_statute_civil', 3, 'STATUTE_CIVIL_CONTRACT', 'Contract Statute', '/STATUTE/CIVIL/CONTRACT', 'STATUTE', 2, 1),
('cat_l3_case_civil_labor', 'cat_l2_case_civil', 3, 'CASE_CIVIL_LABOR', 'Labor Dispute Cases', '/CASE/CIVIL/LABOR', 'CASE', 1, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name);
