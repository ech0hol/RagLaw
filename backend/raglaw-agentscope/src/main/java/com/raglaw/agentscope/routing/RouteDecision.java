package com.raglaw.agentscope.routing;

import java.util.List;

public record RouteDecision(
        TaskType taskType,
        RiskLevel riskLevel,
        ExecutionMode executionMode,
        String expertRole,
        String workflowCode,
        List<String> policyReasons,
        boolean humanApprovalRequired,
        String policyVersion
) {

    public RouteDecision {
        policyReasons = policyReasons == null ? List.of() : List.copyOf(policyReasons);
    }
}
