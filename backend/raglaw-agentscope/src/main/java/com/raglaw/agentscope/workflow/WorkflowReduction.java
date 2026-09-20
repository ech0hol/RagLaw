package com.raglaw.agentscope.workflow;

import java.util.List;
import java.util.Map;

public record WorkflowReduction(List<Map<String, Object>> memoryCandidates,
                                List<WorkflowConflict> conflicts,
                                List<NodeExecutionResult> acceptedResults) {
    public WorkflowReduction {
        memoryCandidates = memoryCandidates == null ? List.of() : List.copyOf(memoryCandidates);
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        acceptedResults = acceptedResults == null ? List.of() : List.copyOf(acceptedResults);
    }
}
