-- Three-level chunk hierarchy: PARENT / CHILD / MICRO (search targets MICRO when present)

ALTER TABLE raglaw_document_chunk
    ADD COLUMN chunk_level VARCHAR(16) NULL AFTER parent_id;

UPDATE raglaw_document_chunk c
INNER JOIN raglaw_document_chunk child
    ON child.parent_id = c.id AND child.document_id = c.document_id
SET c.chunk_level = 'PARENT'
WHERE c.parent_id IS NULL;

UPDATE raglaw_document_chunk
SET chunk_level = 'CHILD'
WHERE parent_id IS NOT NULL
  AND chunk_level IS NULL;

UPDATE raglaw_document_chunk
SET chunk_level = 'CHILD'
WHERE parent_id IS NULL
  AND chunk_level IS NULL;

CREATE INDEX idx_chunk_doc_level ON raglaw_document_chunk (document_id, chunk_level);
