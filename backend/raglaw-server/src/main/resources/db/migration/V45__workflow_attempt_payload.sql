ALTER TABLE raglaw_workflow_node_run
    ADD COLUMN payload_hash VARCHAR(128) NULL,
    ADD COLUMN snapshot_version BIGINT NULL,
    ADD COLUMN owner_id VARCHAR(128) NULL,
    ADD COLUMN lease_until TIMESTAMP(3) NULL;

ALTER TABLE raglaw_workflow_run
    ADD COLUMN workflow_definition_json JSON NULL;
