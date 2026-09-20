package com.raglaw.agentscope.workflow;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface WorkflowRunStore {
    record NodeAttemptCommand(String runId, String nodeCode, String idempotencyKey, String payloadHash, long snapshotVersion, String ownerId) {
        public NodeAttemptCommand(String runId, String nodeCode, String idempotencyKey, String payloadHash, long snapshotVersion) {
            this(runId, nodeCode, idempotencyKey, payloadHash, snapshotVersion, "legacy-owner");
        }
    }
    record StartAttemptResult(String resultId, boolean alreadyAccepted, boolean leaseAcquired) {
        public StartAttemptResult(String resultId, boolean alreadyAccepted) {
            this(resultId, alreadyAccepted, !alreadyAccepted);
        }
    }
    record CompleteAttemptCommand(String runId, String nodeCode, String idempotencyKey, String payloadHash,
                                  String status, Map<String, Object> output, List<String> evidenceIds, long snapshotVersion,
                                  String roleCode, String agentCode, Integer agentVersion, String nodeSessionId,
                                  Map<String, Object> inputRefs, Map<String, Object> toolCallSummary, String ownerId) {
        public CompleteAttemptCommand(String runId, String nodeCode, String idempotencyKey, String payloadHash,
                                      String status, Map<String, Object> output, List<String> evidenceIds, long snapshotVersion) {
            this(runId, nodeCode, idempotencyKey, payloadHash, status, output, evidenceIds, snapshotVersion,
                    null, null, null, null, Map.of(), Map.of(), "legacy-owner");
        }
    }
    StartAttemptResult startAttempt(NodeAttemptCommand command);
    /** Extends the lease for a still-running attempt owned by this coordinator. */
    boolean renewLease(NodeAttemptCommand command);
    NodeExecutionResult completeAttempt(CompleteAttemptCommand command);
    Optional<NodeExecutionResult> findAccepted(NodeAttemptCommand command);
}
