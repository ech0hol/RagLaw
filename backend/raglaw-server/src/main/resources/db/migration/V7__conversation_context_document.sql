ALTER TABLE raglaw_conversation
    ADD COLUMN context_document_id VARCHAR(36) NULL AFTER agent_code,
    ADD INDEX idx_conv_context_doc (context_document_id);
