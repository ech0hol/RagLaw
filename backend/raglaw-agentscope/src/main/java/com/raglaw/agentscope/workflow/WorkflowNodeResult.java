package com.raglaw.agentscope.workflow;

import java.util.List;

/** Immutable, auditable output from one workflow node. */
public record WorkflowNodeResult(String nodeCode, String status, String structuredOutputJson,
                                 List<String> evidenceIds, long latencyMs) {
    public WorkflowNodeResult {
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }
}
