package com.raglaw.agentscope.workflow;

import java.util.List;
import java.util.Map;

public interface WorkflowRunStore {
    record NodeAttemptCommand(String runId, String nodeCode, String idempotencyKey, String payloadHash, long snapshotVersion) {}
    record StartAttemptResult(String resultId, boolean alreadyAccepted) {}
    record CompleteAttemptCommand(String runId, String nodeCode, String idempotencyKey, String payloadHash,
                                  String status, Map<String, Object> output, List<String> evidenceIds, long snapshotVersion) {}
    StartAttemptResult startAttempt(NodeAttemptCommand command);
    NodeExecutionResult completeAttempt(CompleteAttemptCommand command);
}
