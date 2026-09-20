package com.raglaw.agentscope.workflow;

import java.util.List;
import java.util.Set;

public record ResolvedWorkflowNode(String nodeCode, String roleCode, String agentCode, int agentVersion,
                                  Set<String> effectiveTools, String outputSchema, List<String> dependsOn) {
    public ResolvedWorkflowNode {
        if (nodeCode == null || nodeCode.isBlank() || roleCode == null || roleCode.isBlank()) throw new IllegalArgumentException("node and role");
        if (agentCode == null || agentCode.isBlank() || agentVersion <= 0) throw new IllegalArgumentException("agent binding");
        effectiveTools = effectiveTools == null ? Set.of() : Set.copyOf(effectiveTools);
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        outputSchema = outputSchema == null ? "" : outputSchema;
    }
}
