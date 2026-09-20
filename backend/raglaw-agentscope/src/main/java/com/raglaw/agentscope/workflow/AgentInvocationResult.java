package com.raglaw.agentscope.workflow;

import java.util.List;
import java.util.Map;

/** Result returned by one role-bound AgentScope invocation. */
public record AgentInvocationResult(String answer, List<String> evidenceIds, Map<String, Object> metadata) {
    public AgentInvocationResult {
        if (answer == null || answer.isBlank()) throw new IllegalArgumentException("answer");
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
