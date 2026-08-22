CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS raglaw_embedding (
    chunk_id   VARCHAR(36) PRIMARY KEY,
    document_id VARCHAR(36) NOT NULL,
    embedding  vector(1024) NOT NULL,
    l1_path    VARCHAR(128) NOT NULL,
    l2_path    VARCHAR(128) NOT NULL,
    l3_path    VARCHAR(128) NOT NULL,
    doc_type   VARCHAR(32)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_embedding_l3_path ON raglaw_embedding (l3_path);
CREATE INDEX IF NOT EXISTS idx_embedding_doc_type ON raglaw_embedding (doc_type);
CREATE INDEX IF NOT EXISTS idx_embedding_hnsw ON raglaw_embedding USING hnsw (embedding vector_cosine_ops);
