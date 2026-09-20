CREATE TABLE raglaw_conversation_summary (
 id VARCHAR(36) PRIMARY KEY,
 tenant_id VARCHAR(64) NOT NULL,
 user_id VARCHAR(36) NOT NULL,
 case_id VARCHAR(36) NULL,
 conversation_id VARCHAR(36) NOT NULL,
 revision BIGINT NOT NULL,
 summary_json JSON NOT NULL,
 source_message_start VARCHAR(36) NULL,
 source_message_end VARCHAR(36) NULL,
 model_version VARCHAR(128) NULL,
 prompt_version VARCHAR(128) NULL,
 input_tokens INT NULL,
 output_tokens INT NULL,
 created_at TIMESTAMP(3) NOT NULL,
 UNIQUE KEY uk_conversation_summary_revision (conversation_id, revision),
 INDEX idx_conversation_summary_scope (tenant_id, user_id, case_id, conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE raglaw_history_artifact (
 id VARCHAR(36) PRIMARY KEY,
 tenant_id VARCHAR(64) NOT NULL,
 user_id VARCHAR(36) NOT NULL,
 case_id VARCHAR(36) NULL,
 conversation_id VARCHAR(36) NULL,
 workflow_run_id VARCHAR(36) NULL,
 node_code VARCHAR(128) NULL,
 source_id VARCHAR(128) NOT NULL,
 content_type VARCHAR(64) NOT NULL,
 pointer_uri VARCHAR(512) NOT NULL,
 byte_size BIGINT NULL,
 token_estimate INT NULL,
 checksum VARCHAR(128) NOT NULL,
 created_at TIMESTAMP(3) NOT NULL,
 INDEX idx_history_artifact_scope (tenant_id, user_id, case_id, conversation_id),
 INDEX idx_history_artifact_source (source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
