ALTER TABLE raglaw_agent_version
    ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0;
