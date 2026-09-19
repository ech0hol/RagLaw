CREATE TABLE raglaw_workflow_run (
 id VARCHAR(36) PRIMARY KEY, trace_id VARCHAR(36) NOT NULL, route_decision_id VARCHAR(36) NOT NULL,
 workflow_code VARCHAR(128) NOT NULL, status VARCHAR(32) NOT NULL, approval_status VARCHAR(32) NOT NULL,
 started_at TIMESTAMP(3) NOT NULL, completed_at TIMESTAMP(3) NULL, error_code VARCHAR(128) NULL,
 INDEX idx_workflow_run_trace (trace_id), INDEX idx_workflow_run_route (route_decision_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE raglaw_workflow_node_run (
 id VARCHAR(36) PRIMARY KEY, run_id VARCHAR(36) NOT NULL, node_code VARCHAR(128) NOT NULL, attempt INT NOT NULL,
 status VARCHAR(32) NOT NULL, structured_output_json TEXT NULL, evidence_ids_json TEXT NULL, latency_ms BIGINT NULL,
 error_code VARCHAR(128) NULL, INDEX idx_workflow_node_run_run (run_id), INDEX idx_workflow_node_code (run_id,node_code),
 CONSTRAINT fk_workflow_node_run FOREIGN KEY (run_id) REFERENCES raglaw_workflow_run(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
