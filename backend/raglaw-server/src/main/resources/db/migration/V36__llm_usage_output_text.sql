SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'raglaw_llm_usage_log'
      AND COLUMN_NAME = 'output_text'
);
SET @ddl := IF(
    @col_exists = 0,
    'ALTER TABLE raglaw_llm_usage_log ADD COLUMN output_text TEXT NULL COMMENT ''postProcess 后的回答预览''',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
