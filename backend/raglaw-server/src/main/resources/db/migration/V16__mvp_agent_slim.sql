-- MVP agent slim: enable 4 assistants aligned with fixture corpus; disable V6 expert matrix.

UPDATE raglaw_agent_config
SET enabled = 0
WHERE code NOT IN ('GENERAL', 'STATUTE_CIVIL', 'CASE_CIVIL', 'CONTRACT_GENERAL');

UPDATE raglaw_agent_config
SET a2a_peers_json = '["STATUTE_CIVIL","CASE_CIVIL","CONTRACT_GENERAL"]'
WHERE code = 'GENERAL';

UPDATE raglaw_agent_config
SET enabled = 1
WHERE code IN ('GENERAL', 'STATUTE_CIVIL', 'CASE_CIVIL', 'CONTRACT_GENERAL');
