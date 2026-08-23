-- Repair Chinese display names for categories and agents (UTF-8)

-- L1 categories
UPDATE raglaw_category SET name = '法规' WHERE id = 'cat_l1_statute';
UPDATE raglaw_category SET name = '案例' WHERE id = 'cat_l1_case';
UPDATE raglaw_category SET name = '合同' WHERE id = 'cat_l1_contract';

-- L2 statute
UPDATE raglaw_category SET name = '宪法及宪法相关法' WHERE id = 'cat_l2_statute_constitutional';
UPDATE raglaw_category SET name = '民法商法' WHERE id = 'cat_l2_statute_civil';
UPDATE raglaw_category SET name = '行政法' WHERE id = 'cat_l2_statute_admin';
UPDATE raglaw_category SET name = '经济法' WHERE id = 'cat_l2_statute_economic';
UPDATE raglaw_category SET name = '社会法' WHERE id = 'cat_l2_statute_social';
UPDATE raglaw_category SET name = '刑法' WHERE id = 'cat_l2_statute_criminal';
UPDATE raglaw_category SET name = '诉讼与非诉讼程序法' WHERE id = 'cat_l2_statute_procedure';
UPDATE raglaw_category SET name = '生态环境法' WHERE id = 'cat_l2_statute_eco_env';

-- L2 case
UPDATE raglaw_category SET name = '民事案例' WHERE id = 'cat_l2_case_civil';
UPDATE raglaw_category SET name = '刑事案例' WHERE id = 'cat_l2_case_criminal';
UPDATE raglaw_category SET name = '行政案例' WHERE id = 'cat_l2_case_admin';
UPDATE raglaw_category SET name = '诉讼案例' WHERE id = 'cat_l2_case_litigation';

-- L2 contract
UPDATE raglaw_category SET name = '民事合同实务' WHERE id = 'cat_l2_contract_civil';
UPDATE raglaw_category SET name = '刑事合同实务' WHERE id = 'cat_l2_contract_criminal';
UPDATE raglaw_category SET name = '行政合同实务' WHERE id = 'cat_l2_contract_admin';
UPDATE raglaw_category SET name = '诉讼合同实务' WHERE id = 'cat_l2_contract_litigation';

-- L3 categories
UPDATE raglaw_category SET name = '劳动合同法规' WHERE id = 'cat_l3_statute_civil_labor';
UPDATE raglaw_category SET name = '合同法规' WHERE id = 'cat_l3_statute_civil_contract';
UPDATE raglaw_category SET name = '劳动争议案例' WHERE id = 'cat_l3_case_civil_labor';
UPDATE raglaw_category SET name = '民事合同审查' WHERE id = 'cat_l3_contract_civil_general';

-- Core agents
UPDATE raglaw_agent_config SET name = '通用法律助手' WHERE code = 'GENERAL';
UPDATE raglaw_agent_config SET name = '民法商法规范助手' WHERE code = 'STATUTE_CIVIL';
UPDATE raglaw_agent_config SET name = '合同审查通用助手' WHERE code = 'CONTRACT_GENERAL';
UPDATE raglaw_agent_config SET name = '民事案例助手' WHERE code = 'CASE_CIVIL';

-- Expert statute agents
UPDATE raglaw_agent_config SET name = '宪法及宪法相关法助手' WHERE code = 'STATUTE_CONSTITUTIONAL';
UPDATE raglaw_agent_config SET name = '行政法规范助手' WHERE code = 'STATUTE_ADMIN';
UPDATE raglaw_agent_config SET name = '经济法规范助手' WHERE code = 'STATUTE_ECONOMIC';
UPDATE raglaw_agent_config SET name = '社会法规范助手' WHERE code = 'STATUTE_SOCIAL';
UPDATE raglaw_agent_config SET name = '刑法规范助手' WHERE code = 'STATUTE_CRIMINAL';
UPDATE raglaw_agent_config SET name = '诉讼程序法助手' WHERE code = 'STATUTE_PROCEDURE';
UPDATE raglaw_agent_config SET name = '生态环境法助手' WHERE code = 'STATUTE_ECO_ENV';

-- Expert case agents
UPDATE raglaw_agent_config SET name = '刑事案例助手' WHERE code = 'CASE_CRIMINAL';
UPDATE raglaw_agent_config SET name = '行政案例助手' WHERE code = 'CASE_ADMIN';
UPDATE raglaw_agent_config SET name = '诉讼案例助手' WHERE code = 'CASE_LITIGATION';

-- Expert contract agents
UPDATE raglaw_agent_config SET name = '民事合同审查助手' WHERE code = 'CONTRACT_CIVIL';
UPDATE raglaw_agent_config SET name = '刑事合同审查助手' WHERE code = 'CONTRACT_CRIMINAL';
UPDATE raglaw_agent_config SET name = '行政合同审查助手' WHERE code = 'CONTRACT_ADMIN';
UPDATE raglaw_agent_config SET name = '诉讼合同审查助手' WHERE code = 'CONTRACT_LITIGATION';
