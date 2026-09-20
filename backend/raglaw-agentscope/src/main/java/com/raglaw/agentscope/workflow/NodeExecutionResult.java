package com.raglaw.agentscope.workflow;

import java.util.List;
import java.util.Map;

public record NodeExecutionResult(String nodeCode, String idempotencyKey, String status,
                                 Map<String, Object> structuredOutput, List<String> evidenceIds,
                                 long snapshotVersion) {
    public NodeExecutionResult {
        if (nodeCode == null || nodeCode.isBlank() || idempotencyKey == null || idempotencyKey.isBlank()) throw new IllegalArgumentException("node result identity");
        structuredOutput = structuredOutput == null ? Map.of() : Map.copyOf(structuredOutput);
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }
}
