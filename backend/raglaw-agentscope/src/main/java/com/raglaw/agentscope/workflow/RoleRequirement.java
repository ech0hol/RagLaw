package com.raglaw.agentscope.workflow;

import java.util.Set;

public record RoleRequirement(
        String roleCode,
        Set<String> capabilities,
        Set<String> domains,
        Set<String> requiredTools,
        Set<String> requiredInputs,
        String riskLevel,
        double minimumEvaluationScore,
        String defaultAgentCode
) {
    public RoleRequirement {
        if (roleCode == null || roleCode.isBlank()) throw new IllegalArgumentException("roleCode");
        capabilities = normalized(capabilities); domains = normalized(domains); requiredTools = normalizedTools(requiredTools); requiredInputs = normalized(requiredInputs);
        riskLevel = riskLevel == null || riskLevel.isBlank() ? "LOW" : riskLevel.trim().toUpperCase();
        if (minimumEvaluationScore < 0 || minimumEvaluationScore > 1) throw new IllegalArgumentException("minimumEvaluationScore");
        defaultAgentCode = defaultAgentCode == null ? "" : defaultAgentCode.trim();
    }
    private static Set<String> normalized(Set<String> values) { return values == null ? Set.of() : values.stream().filter(v -> v != null && !v.isBlank()).map(v -> v.trim().toUpperCase()).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
    private static Set<String> normalizedTools(Set<String> values) { return values == null ? Set.of() : values.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
}
