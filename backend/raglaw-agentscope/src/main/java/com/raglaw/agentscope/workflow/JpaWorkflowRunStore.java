package com.raglaw.agentscope.workflow;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Idempotency boundary used by the coordinator. The map is deliberately keyed like the
 * database unique key; the production adapter can replace the map with the V43 repository
 * without changing callers.
 */
@Service
public class JpaWorkflowRunStore implements WorkflowRunStore {
    private final Map<String, Entry> attempts = new ConcurrentHashMap<>();

    @Override
    public synchronized StartAttemptResult startAttempt(NodeAttemptCommand command) {
        validate(command.runId(), command.nodeCode(), command.idempotencyKey());
        String key = key(command.runId(), command.nodeCode(), command.idempotencyKey());
        Entry existing = attempts.get(key);
        if (existing != null) {
            if (!existing.payloadHash.equals(command.payloadHash())) throw new IdempotencyConflictException("idempotency key payload mismatch");
            return new StartAttemptResult(command.idempotencyKey(), true);
        }
        attempts.put(key, new Entry(command.payloadHash(), null));
        return new StartAttemptResult(command.idempotencyKey(), false);
    }

    @Override
    public synchronized NodeExecutionResult completeAttempt(CompleteAttemptCommand command) {
        validate(command.runId(), command.nodeCode(), command.idempotencyKey());
        String key = key(command.runId(), command.nodeCode(), command.idempotencyKey());
        Entry existing = attempts.get(key);
        if (existing != null && existing.result != null) {
            if (!existing.payloadHash.equals(command.payloadHash())) throw new IdempotencyConflictException("idempotency key payload mismatch");
            return existing.result;
        }
        if (existing != null && !existing.payloadHash.equals(command.payloadHash())) throw new IdempotencyConflictException("idempotency key payload mismatch");
        NodeExecutionResult result = new NodeExecutionResult(command.nodeCode(), command.idempotencyKey(), command.status(), command.output(), command.evidenceIds(), command.snapshotVersion());
        attempts.put(key, new Entry(command.payloadHash(), result));
        return result;
    }

    private static void validate(String runId, String nodeCode, String key) {
        if (runId == null || runId.isBlank() || nodeCode == null || nodeCode.isBlank() || key == null || key.isBlank()) throw new IllegalArgumentException("attempt identity");
    }
    private static String key(String runId, String nodeCode, String idempotencyKey) { return runId + "|" + nodeCode + "|" + idempotencyKey; }
    private record Entry(String payloadHash, NodeExecutionResult result) {}
}
