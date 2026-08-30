-- Slim expert agents: STATUTE / CASE / CONTRACT (+ GENERAL router).

UPDATE raglaw_agent_config SET code = 'STATUTE', name = '法规助手'
WHERE code = 'STATUTE_CIVIL';

UPDATE raglaw_agent_config SET code = 'CASE', name = '案例助手'
WHERE code = 'CASE_CIVIL';

UPDATE raglaw_agent_config SET code = 'CONTRACT', name = '合同助手'
WHERE code = 'CONTRACT_GENERAL';

UPDATE raglaw_agent_config SET enabled = 0
WHERE code NOT IN ('GENERAL', 'STATUTE', 'CASE', 'CONTRACT');

UPDATE raglaw_agent_config
SET a2a_peers_json = '["STATUTE","CASE","CONTRACT"]'
WHERE code = 'GENERAL';

UPDATE raglaw_agent_config
SET knowledge_scopes_json = '["STATUTE_CONSTITUTIONAL","STATUTE_CIVIL","STATUTE_ADMIN","STATUTE_ECONOMIC","STATUTE_SOCIAL","STATUTE_CRIMINAL","STATUTE_PROCEDURE","STATUTE_ECO_ENV"]'
WHERE code = 'STATUTE';

UPDATE raglaw_agent_config
SET knowledge_scopes_json = '["CASE_CIVIL","CASE_CRIMINAL","CASE_ADMIN","CASE_LITIGATION"]'
WHERE code = 'CASE';

UPDATE raglaw_agent_config
SET knowledge_scopes_json = '["STATUTE_CIVIL","STATUTE_SOCIAL","CASE_CIVIL"]'
WHERE code = 'CONTRACT';

UPDATE raglaw_conversation SET agent_code = 'STATUTE' WHERE agent_code LIKE 'STATUTE_%';
UPDATE raglaw_conversation SET agent_code = 'CASE' WHERE agent_code LIKE 'CASE_%';
UPDATE raglaw_conversation SET agent_code = 'CONTRACT' WHERE agent_code LIKE 'CONTRACT_%';

UPDATE raglaw_rag_trace SET agent_code = 'STATUTE' WHERE agent_code LIKE 'STATUTE_%';
UPDATE raglaw_rag_trace SET agent_code = 'CASE' WHERE agent_code LIKE 'CASE_%';
UPDATE raglaw_rag_trace SET agent_code = 'CONTRACT' WHERE agent_code LIKE 'CONTRACT_%';
