CREATE TABLE IF NOT EXISTS raglaw_shadow_route_log (
    id              VARCHAR(36)  NOT NULL PRIMARY KEY,
    trace_id        VARCHAR(36)  NOT NULL,
    shadow_type     VARCHAR(32)  NOT NULL,
    user_value      VARCHAR(512) NULL,
    system_value    VARCHAR(512) NULL,
    hit             TINYINT(1)   NULL,
    `rank`          INT          NULL,
    confidence_json JSON         NULL,
    created_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_shadow_trace (trace_id),
    INDEX idx_shadow_created (created_at)
);
