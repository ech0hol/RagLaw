package com.raglaw.agentscope.workflow;

import com.raglaw.agentadmin.model.AgentVersionSnapshot;
import java.util.List;
import java.util.Set;

public record AgentResolutionContext(String tenantId, String taskType, String riskLevel,
                                     Set<String> availableInputs, Set<String> userAllowedTools,
                                     Set<String> riskAllowedTools, List<AgentVersionSnapshot> candidates) {
    public AgentResolutionContext {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId");
        taskType = taskType == null || taskType.isBlank() ? "GENERAL_CONSULTATION" : taskType.trim().toUpperCase();
        riskLevel = riskLevel == null || riskLevel.isBlank() ? "LOW" : riskLevel.trim().toUpperCase();
        availableInputs = normalize(availableInputs); userAllowedTools = normalize(userAllowedTools); riskAllowedTools = normalize(riskAllowedTools);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
    private static Set<String> normalize(Set<String> values) { return values == null ? Set.of() : values.stream().filter(v -> v != null && !v.isBlank()).map(v -> v.trim()).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
}
