ALTER TABLE raglaw_document
    ADD COLUMN index_version BIGINT NOT NULL DEFAULT 0 AFTER full_text;

CREATE TABLE IF NOT EXISTS raglaw_index_outbox (
    id           VARCHAR(36) PRIMARY KEY,
    document_id  VARCHAR(36) NOT NULL,
    chunk_id     VARCHAR(36) NULL,
    operation    VARCHAR(16) NOT NULL COMMENT 'UPSERT|DELETE_DOC',
    payload_json JSON NULL,
    index_version BIGINT NOT NULL DEFAULT 0,
    status       VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING|DONE|FAILED',
    error_message TEXT NULL,
    created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_outbox_status (status),
    INDEX idx_outbox_document (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
