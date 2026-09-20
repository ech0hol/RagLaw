package com.raglaw.agentscope.workflow;

import java.util.LinkedHashMap;
import java.util.Map;

public record WorkflowExecutionManifest(String runId, String tenantId, String userId, String caseId,
                                        String conversationId, String workflowCode, int workflowVersion,
                                        String routeDecisionId, long memorySnapshotVersion, String riskPolicyVersion,
                                        String toolPolicyVersion, Map<String, ResolvedWorkflowNode> nodes,
                                        String manifestChecksum) {
    public WorkflowExecutionManifest {
        if (runId == null || runId.isBlank() || tenantId == null || tenantId.isBlank() || userId == null || userId.isBlank() || caseId == null || caseId.isBlank()) throw new IllegalArgumentException("scope");
        if (workflowCode == null || workflowCode.isBlank() || workflowVersion <= 0) throw new IllegalArgumentException("workflow");
        nodes = nodes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(nodes));
        if (nodes.isEmpty()) throw new IllegalArgumentException("nodes");
        manifestChecksum = manifestChecksum == null ? "" : manifestChecksum;
    }
}
