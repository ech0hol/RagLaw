-- Full extracted text from MinIO source files for document-level knowledge search
ALTER TABLE raglaw_document
    ADD COLUMN full_text MEDIUMTEXT NULL AFTER metadata_json;

CREATE FULLTEXT INDEX ft_document_full_text ON raglaw_document (full_text) WITH PARSER ngram;
