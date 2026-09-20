package com.raglaw.agentscope.workflow;

import java.util.List;

public record WorkflowCaseFact(String memoryId, String slot, String value, String status,
                               String visibility, List<String> sourceRefs) {
    public WorkflowCaseFact {
        sourceRefs = sourceRefs == null ? List.of() : List.copyOf(sourceRefs);
        visibility = visibility == null ? "SHARED" : visibility;
    }
}
