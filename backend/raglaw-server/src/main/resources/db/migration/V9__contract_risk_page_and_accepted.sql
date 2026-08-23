ALTER TABLE raglaw_contract_risk
    ADD COLUMN page_number INT NULL AFTER chunk_id,
    ADD COLUMN accepted TINYINT(1) NOT NULL DEFAULT 0 AFTER suggestion;
