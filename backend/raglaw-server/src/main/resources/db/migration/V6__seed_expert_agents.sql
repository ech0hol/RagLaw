-- Expert agent matrix: STATUTE (8 domains) + CASE (4) + CONTRACT (4)

INSERT INTO raglaw_category (id, parent_id, level, code, name, path, doc_type, sort_order, enabled) VALUES
('cat_l3_contract_civil_general', 'cat_l2_contract_civil', 3, 'CONTRACT_CIVIL_GENERAL', 'Civil Contract Review', '/CONTRACT/CIVIL/GENERAL', 'CONTRACT', 1, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO raglaw_agent_config (id, code, name, type, enabled, model, skills_json, knowledge_scopes_json, a2a_peers_json, system_prompt, tools_json) VALUES
('agent_statute_constitutional', 'STATUTE_CONSTITUTIONAL', '宪法及宪法相关法助手', 'STATUTE', 1, 'dashscope:qwen-plus', '[]', '["cat_l2_statute_constitutional"]', '[]', '你是宪法领域法规专家。', '["rag_search"]'),
('agent_statute_admin', 'STATUTE_ADMIN', '行政法规范助手', 'STATUTE', 1, 'dashscope:qwen-plus', '[]', '["cat_l2_statute_admin"]', '[]', '你是行政法领域法规专家。', '["rag_search"]'),
('agent_statute_economic', 'STATUTE_ECONOMIC', '经济法规范助手', 'STATUTE', 1, 'dashscope:qwen-plus', '[]', '["cat_l2_statute_economic"]', '[]', '你是经济法领域法规专家。', '["rag_search"]'),
('agent_statute_social', 'STATUTE_SOCIAL', '社会法规范助手', 'STATUTE', 1, 'dashscope:qwen-plus', '[]', '["cat_l2_statute_social"]', '[]', '你是社会法（含劳动）领域法规专家。', '["rag_search"]'),
('agent_statute_criminal', 'STATUTE_CRIMINAL', '刑法规范助手', 'STATUTE', 1, 'dashscope:qwen-plus', '[]', '["cat_l2_statute_criminal"]', '[]', '你是刑法领域法规专家。', '["rag_search"]'),
('agent_statute_procedure', 'STATUTE_PROCEDURE', '诉讼程序法助手', 'STATUTE', 1, 'dashscope:qwen-plus', '[]', '["cat_l2_statute_procedure"]', '[]', '你是诉讼与非诉讼程序法专家。', '["rag_search"]'),
('agent_statute_eco_env', 'STATUTE_ECO_ENV', '生态环境法助手', 'STATUTE', 1, 'dashscope:qwen-plus', '[]', '["cat_l2_statute_eco_env"]', '[]', '你是生态环境法领域专家。', '["rag_search"]'),
('agent_case_criminal', 'CASE_CRIMINAL', '刑事案例助手', 'CASE', 1, 'dashscope:qwen-plus', '[]', '["cat_l2_case_criminal"]', '[]', '你是刑事案例检索专家。', '["rag_search"]'),
('agent_case_admin', 'CASE_ADMIN', '行政案例助手', 'CASE', 1, 'dashscope:qwen-plus', '[]', '["cat_l2_case_admin"]', '[]', '你是行政案例检索专家。', '["rag_search"]'),
('agent_case_litigation', 'CASE_LITIGATION', '诉讼案例助手', 'CASE', 1, 'dashscope:qwen-plus', '[]', '["cat_l2_case_litigation"]', '[]', '你是诉讼案例检索专家。', '["rag_search"]'),
('agent_contract_civil', 'CONTRACT_CIVIL', '民事合同审查助手', 'CONTRACT', 1, 'dashscope:qwen-max', '["risk-dimension-review"]', '["cat_l2_contract_civil"]', '[]', '你是民事合同审查专家。', '["rag_search"]'),
('agent_contract_criminal', 'CONTRACT_CRIMINAL', '刑事合同审查助手', 'CONTRACT', 1, 'dashscope:qwen-max', '["risk-dimension-review"]', '["cat_l2_contract_criminal"]', '[]', '你是刑事相关合同审查专家。', '["rag_search"]'),
('agent_contract_admin', 'CONTRACT_ADMIN', '行政合同审查助手', 'CONTRACT', 1, 'dashscope:qwen-max', '["risk-dimension-review"]', '["cat_l2_contract_admin"]', '[]', '你是行政合同审查专家。', '["rag_search"]'),
('agent_contract_litigation', 'CONTRACT_LITIGATION', '诉讼合同审查助手', 'CONTRACT', 1, 'dashscope:qwen-max', '["risk-dimension-review"]', '["cat_l2_contract_litigation"]', '[]', '你是诉讼相关合同审查专家。', '["rag_search"]')
ON DUPLICATE KEY UPDATE name = VALUES(name), knowledge_scopes_json = VALUES(knowledge_scopes_json);

UPDATE raglaw_agent_config
SET a2a_peers_json = '["STATUTE_SOCIAL","STATUTE_CIVIL","STATUTE_ADMIN","STATUTE_ECONOMIC","STATUTE_CRIMINAL","STATUTE_PROCEDURE","STATUTE_ECO_ENV","STATUTE_CONSTITUTIONAL","CASE_CIVIL","CASE_CRIMINAL","CASE_ADMIN","CASE_LITIGATION","CONTRACT_GENERAL","CONTRACT_CIVIL"]'
WHERE code = 'GENERAL';
