ALTER TABLE raglaw_document
  ADD COLUMN ingest_stage VARCHAR(32) NULL AFTER status,
  ADD COLUMN ingest_error TEXT NULL AFTER ingest_stage;
