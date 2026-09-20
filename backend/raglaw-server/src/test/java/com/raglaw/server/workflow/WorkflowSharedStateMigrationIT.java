package com.raglaw.server.workflow;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WorkflowSharedStateMigrationIT {
    @Test
    void sharedStateMigrationDefinesDurableManifestAndIdempotencyBoundary() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("db/migration/V43__workflow_shared_state.sql")) {
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
            assertTrue(sql.contains("manifest_json"));
            assertTrue(sql.contains("lock_version"));
            assertTrue(sql.contains("uk_workflow_node_idempotency"));
            assertTrue(sql.contains("raglaw_workflow_conflict"));
        }
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("db/migration/V45__workflow_attempt_payload.sql")) {
            String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
            assertTrue(sql.contains("payload_hash"));
            assertTrue(sql.contains("snapshot_version"));
            assertTrue(sql.contains("workflow_definition_json"));
        }
    }
}
