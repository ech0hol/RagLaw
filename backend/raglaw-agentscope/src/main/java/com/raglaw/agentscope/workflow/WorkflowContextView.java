package com.raglaw.agentscope.workflow;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record WorkflowContextView(String runId, String nodeCode, long memorySnapshotVersion,
                                  List<WorkflowCaseFact> caseFacts, WorkflowTaskNote taskNote,
                                  Map<String, NodeExecutionResult> dependencyResults,
                                  List<WorkflowEvidenceReference> evidenceReferences,
                                  Set<String> effectiveTools) {
    public WorkflowContextView {
        caseFacts = caseFacts == null ? List.of() : List.copyOf(caseFacts);
        dependencyResults = dependencyResults == null ? Map.of() : Map.copyOf(dependencyResults);
        evidenceReferences = evidenceReferences == null ? List.of() : List.copyOf(evidenceReferences);
        effectiveTools = effectiveTools == null ? Set.of() : Set.copyOf(effectiveTools);
    }
}
