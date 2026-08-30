-- Enable domain expert agents aligned with L2 statute/case categories.

UPDATE raglaw_agent_config
SET enabled = 1
WHERE code IN (
  'STATUTE_CONSTITUTIONAL',
  'STATUTE_ADMIN',
  'STATUTE_ECONOMIC',
  'STATUTE_SOCIAL',
  'STATUTE_CRIMINAL',
  'STATUTE_PROCEDURE',
  'STATUTE_ECO_ENV',
  'CASE_CRIMINAL',
  'CASE_ADMIN',
  'CASE_LITIGATION'
);

-- Normalize knowledgeScopes: replace legacy category ids with codes for L2 bindings.
UPDATE raglaw_agent_config
SET knowledge_scopes_json = '["STATUTE_CIVIL"]'
WHERE code = 'STATUTE_CIVIL' AND knowledge_scopes_json LIKE '%cat_l2_statute_civil%';

UPDATE raglaw_agent_config
SET knowledge_scopes_json = '["CASE_CIVIL"]'
WHERE code = 'CASE_CIVIL' AND knowledge_scopes_json LIKE '%cat_l2_case_civil%';

UPDATE raglaw_agent_config
SET knowledge_scopes_json = '["CONTRACT_CIVIL"]'
WHERE code = 'CONTRACT_GENERAL' AND knowledge_scopes_json LIKE '%cat_l2_contract_civil%';
