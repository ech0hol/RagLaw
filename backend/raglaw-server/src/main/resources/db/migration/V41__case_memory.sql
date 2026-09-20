CREATE TABLE raglaw_case (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    title VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    INDEX idx_case_owner (tenant_id, user_id, updated_at),
    CONSTRAINT fk_case_user FOREIGN KEY (user_id) REFERENCES raglaw_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE raglaw_conversation ADD COLUMN case_id VARCHAR(36) NULL;
ALTER TABLE raglaw_conversation ADD INDEX idx_conv_case (case_id);
ALTER TABLE raglaw_conversation ADD CONSTRAINT fk_conv_case FOREIGN KEY (case_id) REFERENCES raglaw_case(id);

CREATE TABLE raglaw_case_memory (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    case_id VARCHAR(36) NOT NULL,
    subject_type VARCHAR(64) NOT NULL,
    subject_id VARCHAR(128) NOT NULL,
    predicate VARCHAR(128) NOT NULL,
    value_json JSON NOT NULL,
    lifecycle VARCHAR(32) NOT NULL,
    verification VARCHAR(32) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_id VARCHAR(128) NOT NULL,
    valid_from TIMESTAMP(3) NULL,
    valid_to TIMESTAMP(3) NULL,
    supersedes_id VARCHAR(36) NULL,
    replacement_memory_id VARCHAR(36) NULL,
    case_sequence BIGINT NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL,
    INDEX idx_memory_scope_state (tenant_id, user_id, case_id, lifecycle, case_sequence),
    INDEX idx_memory_slot (tenant_id, user_id, case_id, subject_type, subject_id, predicate),
    INDEX idx_memory_source (tenant_id, user_id, case_id, source_id),
    CONSTRAINT fk_memory_case FOREIGN KEY (case_id) REFERENCES raglaw_case(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE raglaw_memory_audit (
    id VARCHAR(36) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    case_id VARCHAR(36) NOT NULL,
    candidate_json JSON NOT NULL,
    proposed_action VARCHAR(32) NOT NULL,
    applied_action VARCHAR(32) NOT NULL,
    reason VARCHAR(512) NOT NULL,
    actor VARCHAR(128) NOT NULL,
    old_memory_ids_json JSON NULL,
    new_memory_ids_json JSON NULL,
    created_at TIMESTAMP(3) NOT NULL,
    INDEX idx_memory_audit_scope (tenant_id, user_id, case_id, created_at),
    CONSTRAINT fk_memory_audit_case FOREIGN KEY (case_id) REFERENCES raglaw_case(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE raglaw_case_memory_version (
    tenant_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    case_id VARCHAR(36) NOT NULL,
    latest_sequence BIGINT NOT NULL DEFAULT 0,
    lock_version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (tenant_id, user_id, case_id),
    CONSTRAINT fk_memory_version_case FOREIGN KEY (case_id) REFERENCES raglaw_case(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
