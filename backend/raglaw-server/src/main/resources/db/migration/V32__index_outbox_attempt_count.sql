ALTER TABLE raglaw_index_outbox
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER status;
