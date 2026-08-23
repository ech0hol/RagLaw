-- L1/L2 category seed

INSERT INTO raglaw_category (id, parent_id, level, code, name, path, doc_type, sort_order, enabled) VALUES
('cat_l1_statute', NULL, 1, 'STATUTE', '法规', '/STATUTE', 'STATUTE', 1, 1),
('cat_l1_case', NULL, 1, 'CASE', '案例', '/CASE', 'CASE', 2, 1),
('cat_l1_contract', NULL, 1, 'CONTRACT', '合同', '/CONTRACT', 'CONTRACT', 3, 1);

-- L2 法规
INSERT INTO raglaw_category (id, parent_id, level, code, name, path, doc_type, sort_order, enabled) VALUES
('cat_l2_statute_constitutional', 'cat_l1_statute', 2, 'STATUTE_CONSTITUTIONAL', '宪法及宪法相关法', '/STATUTE/CONSTITUTIONAL', 'STATUTE', 1, 1),
('cat_l2_statute_civil', 'cat_l1_statute', 2, 'STATUTE_CIVIL', '民法商法', '/STATUTE/CIVIL', 'STATUTE', 2, 1),
('cat_l2_statute_admin', 'cat_l1_statute', 2, 'STATUTE_ADMIN', '行政法', '/STATUTE/ADMIN', 'STATUTE', 3, 1),
('cat_l2_statute_economic', 'cat_l1_statute', 2, 'STATUTE_ECONOMIC', '经济法', '/STATUTE/ECONOMIC', 'STATUTE', 4, 1),
('cat_l2_statute_social', 'cat_l1_statute', 2, 'STATUTE_SOCIAL', '社会法', '/STATUTE/SOCIAL', 'STATUTE', 5, 1),
('cat_l2_statute_criminal', 'cat_l1_statute', 2, 'STATUTE_CRIMINAL', '刑法', '/STATUTE/CRIMINAL', 'STATUTE', 6, 1),
('cat_l2_statute_procedure', 'cat_l1_statute', 2, 'STATUTE_PROCEDURE', '诉讼与非诉讼程序法', '/STATUTE/PROCEDURE', 'STATUTE', 7, 1),
('cat_l2_statute_eco_env', 'cat_l1_statute', 2, 'STATUTE_ECO_ENV', '生态环境法', '/STATUTE/ECO_ENV', 'STATUTE', 8, 1);

-- L2 案例
INSERT INTO raglaw_category (id, parent_id, level, code, name, path, doc_type, sort_order, enabled) VALUES
('cat_l2_case_civil', 'cat_l1_case', 2, 'CASE_CIVIL', '民事案例', '/CASE/CIVIL', 'CASE', 1, 1),
('cat_l2_case_criminal', 'cat_l1_case', 2, 'CASE_CRIMINAL', '刑事案例', '/CASE/CRIMINAL', 'CASE', 2, 1),
('cat_l2_case_admin', 'cat_l1_case', 2, 'CASE_ADMIN', '行政案例', '/CASE/ADMIN', 'CASE', 3, 1),
('cat_l2_case_litigation', 'cat_l1_case', 2, 'CASE_LITIGATION', '诉讼案例', '/CASE/LITIGATION', 'CASE', 4, 1);

-- L2 合同
INSERT INTO raglaw_category (id, parent_id, level, code, name, path, doc_type, sort_order, enabled) VALUES
('cat_l2_contract_civil', 'cat_l1_contract', 2, 'CONTRACT_CIVIL', '民事合同实务', '/CONTRACT/CIVIL', 'CONTRACT', 1, 1),
('cat_l2_contract_criminal', 'cat_l1_contract', 2, 'CONTRACT_CRIMINAL', '刑事合同实务', '/CONTRACT/CRIMINAL', 'CONTRACT', 2, 1),
('cat_l2_contract_admin', 'cat_l1_contract', 2, 'CONTRACT_ADMIN', '行政合同实务', '/CONTRACT/ADMIN', 'CONTRACT', 3, 1),
('cat_l2_contract_litigation', 'cat_l1_contract', 2, 'CONTRACT_LITIGATION', '诉讼合同实务', '/CONTRACT/LITIGATION', 'CONTRACT', 4, 1);
