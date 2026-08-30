-- MVP category slim: enable 10-node tree aligned with fixture corpus; disable V6 L2 matrix.

UPDATE raglaw_category SET name = '劳动合同法规' WHERE id = 'cat_l3_statute_civil_labor';
UPDATE raglaw_category SET name = '合同法规' WHERE id = 'cat_l3_statute_civil_contract';
UPDATE raglaw_category SET name = '劳动争议案例' WHERE id = 'cat_l3_case_civil_labor';
UPDATE raglaw_category SET name = '民事合同审查' WHERE id = 'cat_l3_contract_civil_general';

UPDATE raglaw_category
SET enabled = 0
WHERE level = 2
  AND id NOT IN (
    'cat_l2_statute_civil',
    'cat_l2_case_civil',
    'cat_l2_contract_civil'
  );

UPDATE raglaw_category
SET enabled = 1
WHERE id IN (
  'cat_l1_statute', 'cat_l1_case', 'cat_l1_contract',
  'cat_l2_statute_civil', 'cat_l2_case_civil', 'cat_l2_contract_civil',
  'cat_l3_statute_civil_labor', 'cat_l3_statute_civil_contract',
  'cat_l3_case_civil_labor', 'cat_l3_contract_civil_general'
);
