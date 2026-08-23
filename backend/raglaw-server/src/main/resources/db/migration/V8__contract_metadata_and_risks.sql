ALTER TABLE raglaw_document
    ADD COLUMN metadata_json JSON NULL AFTER minio_key;

CREATE TABLE IF NOT EXISTS raglaw_contract_risk (
    id           VARCHAR(36) PRIMARY KEY,
    document_id  VARCHAR(36) NOT NULL,
    chunk_id     VARCHAR(36) NULL,
    severity     VARCHAR(16) NOT NULL COMMENT 'HIGH|MEDIUM|LOW',
    dimension    VARCHAR(64) NOT NULL,
    summary      VARCHAR(512) NOT NULL,
    excerpt      TEXT NULL,
    suggestion   TEXT NULL,
    created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_contract_risk_doc (document_id),
    CONSTRAINT fk_contract_risk_doc FOREIGN KEY (document_id) REFERENCES raglaw_document(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
