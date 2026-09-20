ALTER TABLE raglaw_agent_version
    ADD COLUMN transitioned_at TIMESTAMP NULL,
    ADD COLUMN transitioned_by VARCHAR(128) NULL;
