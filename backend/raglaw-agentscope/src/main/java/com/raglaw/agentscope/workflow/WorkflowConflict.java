package com.raglaw.agentscope.workflow;

import java.util.List;

public record WorkflowConflict(String slot, List<String> resultIds, List<String> values, boolean requiresApproval) {
    public WorkflowConflict {
        resultIds = resultIds == null ? List.of() : List.copyOf(resultIds);
        values = values == null ? List.of() : List.copyOf(values);
    }
}
