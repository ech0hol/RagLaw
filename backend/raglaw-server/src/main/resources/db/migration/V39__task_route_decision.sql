CREATE TABLE raglaw_task_route_decision (
    id VARCHAR(36) PRIMARY KEY,
    trace_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    actual_expert_code VARCHAR(128) NOT NULL,
    actual_expert_name VARCHAR(256),
    candidate_task_type VARCHAR(64) NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    execution_mode VARCHAR(48) NOT NULL,
    expert_role VARCHAR(128),
    workflow_code VARCHAR(128),
    agreement BOOLEAN NOT NULL,
    classifier_latency_ms BIGINT,
    prompt_version VARCHAR(128),
    model_version VARCHAR(128),
    policy_version VARCHAR(128),
    policy_reasons_json TEXT,
    missing_materials_json TEXT,
    error_code VARCHAR(128)
);
CREATE INDEX idx_task_route_decision_trace ON raglaw_task_route_decision(trace_id);
CREATE INDEX idx_task_route_decision_created ON raglaw_task_route_decision(created_at);
CREATE INDEX idx_task_route_decision_risk ON raglaw_task_route_decision(risk_level);
CREATE INDEX idx_task_route_decision_mode ON raglaw_task_route_decision(execution_mode);
CREATE INDEX idx_task_route_decision_task_created ON raglaw_task_route_decision(candidate_task_type, created_at);
