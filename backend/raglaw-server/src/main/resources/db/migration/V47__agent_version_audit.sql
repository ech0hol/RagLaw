ALTER TABLE raglaw_agent_version
    ADD COLUMN created_by VARCHAR(128) NOT NULL DEFAULT 'system';
