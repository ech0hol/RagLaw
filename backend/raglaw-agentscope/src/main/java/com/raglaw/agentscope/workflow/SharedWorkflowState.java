package com.raglaw.agentscope.workflow;

import com.raglaw.memory.service.CaseMemorySnapshot;
import java.util.List;
import java.util.Map;

public record SharedWorkflowState(WorkflowExecutionManifest manifest, CaseMemorySnapshot caseSnapshot,
                                  WorkflowTaskNote taskNote, List<WorkflowCaseFact> caseFacts,
                                  List<WorkflowEvidenceReference> evidenceReferences,
                                  Map<String, NodeExecutionResult> completedResults) {
    public SharedWorkflowState {
        if (manifest == null || caseSnapshot == null) throw new IllegalArgumentException("snapshot");
        caseFacts = caseFacts == null ? List.of() : List.copyOf(caseFacts);
        evidenceReferences = evidenceReferences == null ? List.of() : List.copyOf(evidenceReferences);
        completedResults = completedResults == null ? Map.of() : Map.copyOf(completedResults);
    }
}
