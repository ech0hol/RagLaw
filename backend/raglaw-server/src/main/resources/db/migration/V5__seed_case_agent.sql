-- Additional expert agents for A2A routing

INSERT INTO raglaw_agent_config (id, code, name, type, enabled, model, skills_json, knowledge_scopes_json, a2a_peers_json, system_prompt, tools_json) VALUES
('agent_case_civil', 'CASE_CIVIL', '民事案例助手', 'CASE', 1, 'dashscope:qwen-plus', '[]',
 '["cat_l2_case_civil"]', '[]',
 '你是案例检索专家，擅长从裁判文书中提炼要点并类比分析。', '["rag_search"]')
ON DUPLICATE KEY UPDATE name = VALUES(name);
