ALTER TABLE raglaw_workflow_run
    ADD COLUMN tenant_id VARCHAR(64) NULL,
    ADD COLUMN user_id VARCHAR(36) NULL,
    ADD COLUMN case_id VARCHAR(36) NULL,
    ADD COLUMN conversation_id VARCHAR(36) NULL,
    ADD COLUMN workflow_version INT NULL,
    ADD COLUMN memory_snapshot_version BIGINT NULL,
    ADD COLUMN manifest_json JSON NULL,
    ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE raglaw_workflow_node_run
    ADD COLUMN role_code VARCHAR(128) NULL,
    ADD COLUMN agent_code VARCHAR(128) NULL,
    ADD COLUMN agent_version INT NULL,
    ADD COLUMN node_session_id VARCHAR(36) NULL,
    ADD COLUMN idempotency_key VARCHAR(128) NULL,
    ADD COLUMN input_refs_json JSON NULL,
    ADD COLUMN output_revision BIGINT NULL,
    ADD COLUMN started_at TIMESTAMP(3) NULL,
    ADD COLUMN completed_at TIMESTAMP(3) NULL,
    ADD COLUMN tool_call_summary_json JSON NULL,
    ADD UNIQUE KEY uk_workflow_node_idempotency (run_id, node_code, idempotency_key);

CREATE TABLE raglaw_workflow_conflict (
 id VARCHAR(36) PRIMARY KEY,
 run_id VARCHAR(36) NOT NULL,
 node_result_ids_json JSON NOT NULL,
 slot VARCHAR(256) NOT NULL,
 conflict_type VARCHAR(64) NOT NULL,
 status VARCHAR(32) NOT NULL,
 created_at TIMESTAMP(3) NOT NULL,
 INDEX idx_workflow_conflict_run (run_id),
 CONSTRAINT fk_workflow_conflict_run FOREIGN KEY (run_id) REFERENCES raglaw_workflow_run(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
