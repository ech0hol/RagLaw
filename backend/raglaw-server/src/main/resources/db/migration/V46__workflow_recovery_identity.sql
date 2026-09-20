ALTER TABLE raglaw_workflow_run
    ADD COLUMN workflow_definition_hash VARCHAR(128) NULL,
    ADD COLUMN input_hash VARCHAR(128) NULL;
