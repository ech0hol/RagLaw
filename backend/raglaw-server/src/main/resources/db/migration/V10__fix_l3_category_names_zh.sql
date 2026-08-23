-- Fix L3 category display names (replace English placeholder seeds with Chinese)

UPDATE raglaw_category SET name = '劳动合同法规' WHERE id = 'cat_l3_statute_civil_labor';
UPDATE raglaw_category SET name = '合同法规' WHERE id = 'cat_l3_statute_civil_contract';
UPDATE raglaw_category SET name = '劳动争议案例' WHERE id = 'cat_l3_case_civil_labor';
UPDATE raglaw_category SET name = '民事合同审查' WHERE id = 'cat_l3_contract_civil_general';
