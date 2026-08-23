-- RagLaw MySQL schema (utf8mb4 + ngram fulltext)

CREATE TABLE IF NOT EXISTS raglaw_user (
    id            VARCHAR(36)  PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name  VARCHAR(128) NOT NULL,
    role          VARCHAR(32)  NOT NULL COMMENT 'LAWYER | ADMIN',
    enabled       TINYINT(1)   NOT NULL DEFAULT 1,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS raglaw_conversation (
    id          VARCHAR(36) PRIMARY KEY,
    user_id     VARCHAR(36) NOT NULL,
    title       VARCHAR(255) NOT NULL DEFAULT '新对话',
    agent_code  VARCHAR(64) NOT NULL DEFAULT 'GENERAL',
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_conv_user_updated (user_id, updated_at DESC),
    CONSTRAINT fk_conv_user FOREIGN KEY (user_id) REFERENCES raglaw_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS raglaw_message (
    id              VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    role            VARCHAR(16) NOT NULL COMMENT 'user | assistant | system',
    content         MEDIUMTEXT  NOT NULL,
    citations_json  JSON        NULL,
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_msg_conv (conversation_id, created_at),
    CONSTRAINT fk_msg_conv FOREIGN KEY (conversation_id) REFERENCES raglaw_conversation(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS raglaw_category (
    id         VARCHAR(36) PRIMARY KEY,
    parent_id  VARCHAR(36) NULL,
    level      TINYINT     NOT NULL COMMENT '1|2|3',
    code       VARCHAR(64) NOT NULL,
    name       VARCHAR(128) NOT NULL,
    path       VARCHAR(512) NOT NULL,
    doc_type   VARCHAR(32) NOT NULL COMMENT 'CASE|STATUTE|CONTRACT',
    sort_order INT         NOT NULL DEFAULT 0,
    enabled    TINYINT(1)  NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_category_code (code),
    INDEX idx_category_parent (parent_id),
    INDEX idx_category_path (path(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS raglaw_agent_config (
    id               VARCHAR(36) PRIMARY KEY,
    code             VARCHAR(64)  NOT NULL UNIQUE,
    name             VARCHAR(128) NOT NULL,
    type             VARCHAR(32)  NOT NULL,
    enabled          TINYINT(1)   NOT NULL DEFAULT 1,
    model            VARCHAR(128) NOT NULL DEFAULT 'dashscope:qwen-plus',
    skills_json      JSON         NULL,
    mcp_servers_json JSON         NULL,
    knowledge_scopes_json JSON    NULL,
    a2a_peers_json   JSON         NULL,
    system_prompt    MEDIUMTEXT   NULL,
    tools_json       JSON         NULL,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS raglaw_document (
    id           VARCHAR(36) PRIMARY KEY,
    category_id  VARCHAR(36) NOT NULL,
    title        VARCHAR(512) NOT NULL,
    doc_type     VARCHAR(32)  NOT NULL,
    status       VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    uploader_id  VARCHAR(36)  NULL,
    minio_key    VARCHAR(512) NULL,
    reject_reason VARCHAR(512) NULL,
    created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_doc_category (category_id),
    INDEX idx_doc_status (status),
    CONSTRAINT fk_doc_category FOREIGN KEY (category_id) REFERENCES raglaw_category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS raglaw_document_chunk (
    id           VARCHAR(36) PRIMARY KEY,
    document_id  VARCHAR(36) NOT NULL,
    parent_id    VARCHAR(36) NULL,
    chunk_index  INT         NOT NULL,
    content      MEDIUMTEXT  NOT NULL,
    l1_path      VARCHAR(128) NOT NULL,
    l2_path      VARCHAR(128) NOT NULL,
    l3_path      VARCHAR(128) NOT NULL,
    metadata_json JSON       NULL,
    created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_chunk_doc (document_id),
    FULLTEXT INDEX ft_chunk_content (content) WITH PARSER ngram,
    CONSTRAINT fk_chunk_doc FOREIGN KEY (document_id) REFERENCES raglaw_document(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS raglaw_statute_ref (
    id              VARCHAR(36) PRIMARY KEY,
    from_statute_id VARCHAR(36) NOT NULL,
    to_statute_id   VARCHAR(36) NOT NULL,
    ref_type        VARCHAR(32) NOT NULL DEFAULT 'CITE',
    created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_statute_ref_from (from_statute_id),
    INDEX idx_statute_ref_to (to_statute_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS raglaw_case_statute_ref (
    id          VARCHAR(36) PRIMARY KEY,
    case_doc_id VARCHAR(36) NOT NULL,
    statute_id  VARCHAR(36) NOT NULL,
    ref_type    VARCHAR(32) NOT NULL DEFAULT 'APPLY',
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_case_ref_case (case_doc_id),
    INDEX idx_case_ref_statute (statute_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS raglaw_rag_trace (
    id                 VARCHAR(36) PRIMARY KEY,
    conversation_id    VARCHAR(36) NULL,
    message_id         VARCHAR(36) NULL,
    user_id            VARCHAR(36) NULL,
    query_text         TEXT         NULL,
    agent_code         VARCHAR(64)  NULL,
    langfuse_trace_id  VARCHAR(128) NULL,
    latency_ms         BIGINT       NULL,
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_trace_created (created_at DESC),
    INDEX idx_trace_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS raglaw_rag_trace_stage (
    id          VARCHAR(36) PRIMARY KEY,
    trace_id    VARCHAR(36) NOT NULL,
    stage       VARCHAR(64) NOT NULL,
    detail_json JSON        NULL,
    duration_ms BIGINT      NULL,
    INDEX idx_trace_stage (trace_id),
    CONSTRAINT fk_trace_stage FOREIGN KEY (trace_id) REFERENCES raglaw_rag_trace(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS raglaw_rag_trace_chunk (
    id           VARCHAR(36) PRIMARY KEY,
    trace_id     VARCHAR(36) NOT NULL,
    chunk_id     VARCHAR(36) NULL,
    score        DOUBLE       NULL,
    l1_l2_l3_path VARCHAR(512) NULL,
    excerpt      TEXT         NULL,
    INDEX idx_trace_chunk (trace_id),
    CONSTRAINT fk_trace_chunk FOREIGN KEY (trace_id) REFERENCES raglaw_rag_trace(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS raglaw_a2a_call_log (
    id             VARCHAR(36) PRIMARY KEY,
    trace_id       VARCHAR(36) NOT NULL,
    from_agent     VARCHAR(64) NOT NULL,
    to_agent       VARCHAR(64) NOT NULL,
    input_summary  TEXT        NULL,
    output_summary TEXT        NULL,
    latency_ms     BIGINT      NULL,
    INDEX idx_a2a_trace (trace_id),
    CONSTRAINT fk_a2a_trace FOREIGN KEY (trace_id) REFERENCES raglaw_rag_trace(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS raglaw_llm_usage_log (
    id                VARCHAR(36) PRIMARY KEY,
    trace_id          VARCHAR(36) NOT NULL,
    model             VARCHAR(128) NOT NULL,
    prompt_tokens     INT          NULL,
    completion_tokens INT          NULL,
    INDEX idx_llm_trace (trace_id),
    CONSTRAINT fk_llm_trace FOREIGN KEY (trace_id) REFERENCES raglaw_rag_trace(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
