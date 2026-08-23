-- Agent config seed

INSERT INTO raglaw_agent_config (id, code, name, type, enabled, model, skills_json, knowledge_scopes_json, a2a_peers_json, system_prompt, tools_json) VALUES
('agent_general', 'GENERAL', '通用法律助手', 'GENERAL', 1, 'dashscope:qwen-plus', '[]',
 '[]', '["STATUTE_CIVIL","CASE_CIVIL","CONTRACT_GENERAL"]',
 '你是通用法律助手，负责理解用户问题并协调专家助手回答。', '[]'),

('agent_statute_civil', 'STATUTE_CIVIL', '民法商法规范助手', 'STATUTE', 1, 'dashscope:qwen-plus', '[]',
 '["cat_l2_statute_civil"]', '[]',
 '你是法规专家，专注民法商法领域，回答需引用依据。', '["rag_search"]'),

('agent_contract_general', 'CONTRACT_GENERAL', '合同审查通用助手', 'CONTRACT', 1, 'dashscope:qwen-max', '["risk-dimension-review"]',
 '["cat_l2_contract_civil"]', '[]',
 '你是合同审查专家，识别风险并给出修订建议。', '["rag_search"]');
