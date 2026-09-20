ALTER TABLE raglaw_agent_config
    ADD COLUMN description VARCHAR(512) NULL,
    ADD COLUMN owner_id VARCHAR(128) NULL,
    ADD COLUMN current_draft_version INT NULL;

CREATE TABLE raglaw_agent_version (
 id VARCHAR(36) PRIMARY KEY,
 agent_code VARCHAR(64) NOT NULL,
 version INT NOT NULL,
 status VARCHAR(32) NOT NULL,
 model VARCHAR(128) NOT NULL,
 system_prompt MEDIUMTEXT NOT NULL,
 manifest_json JSON NOT NULL,
 tool_policy_json JSON NOT NULL,
 skills_json JSON NOT NULL,
 knowledge_scopes_json JSON NOT NULL,
 mcp_servers_json JSON NOT NULL,
 evaluation_score DECIMAL(6,5) NOT NULL,
 config_checksum VARCHAR(128) NOT NULL,
 created_at TIMESTAMP(3) NOT NULL,
 published_at TIMESTAMP(3) NULL,
 published_by VARCHAR(128) NULL,
 UNIQUE KEY uk_agent_version_code (agent_code, version),
 INDEX idx_agent_version_status (status),
 INDEX idx_agent_version_score (status, evaluation_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE raglaw_agent_resolution_observation (
 id VARCHAR(36) PRIMARY KEY,
 trace_id VARCHAR(36) NOT NULL,
 role_code VARCHAR(128) NOT NULL,
 deterministic_winner VARCHAR(128) NOT NULL,
 model_winner VARCHAR(128) NULL,
 agreement BOOLEAN NOT NULL,
 confidence DECIMAL(6,5) NULL,
 candidate_versions_json JSON NOT NULL,
 created_at TIMESTAMP(3) NOT NULL,
 INDEX idx_agent_resolution_trace (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
