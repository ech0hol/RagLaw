package com.raglaw.agentscope.workflow;

import java.util.List;

public record WorkflowNodeDefinition(
        String code,
        String requiredRole,
        List<String> dependsOn,
        boolean parallelEligible
) {
    public WorkflowNodeDefinition {
        requireText(code, "code");
        requireText(requiredRole, "requiredRole");
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        if (dependsOn.stream().anyMatch(dependency -> dependency == null || dependency.isBlank())) {
            throw new IllegalArgumentException("dependsOn must not contain blank codes");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
