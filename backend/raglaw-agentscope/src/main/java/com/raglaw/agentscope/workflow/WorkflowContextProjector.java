package com.raglaw.agentscope.workflow;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class WorkflowContextProjector {
    public WorkflowContextView project(String nodeCode, SharedWorkflowState state) {
        ResolvedWorkflowNode node = state.manifest().nodes().get(nodeCode);
        if (node == null) throw new IllegalArgumentException("unknown node: " + nodeCode);
        Map<String, NodeExecutionResult> dependencies = new LinkedHashMap<>();
        for (String dependency : node.dependsOn()) {
            NodeExecutionResult result = state.completedResults().get(dependency);
            if (result != null) dependencies.put(dependency, result);
        }
        return new WorkflowContextView(state.manifest().runId(), nodeCode, state.caseSnapshot().version(),
                state.caseFacts().stream().filter(fact -> !"PRIVATE_OTHER_ROLE".equals(fact.visibility())).toList(),
                state.taskNote(), dependencies, state.evidenceReferences(), node.effectiveTools());
    }
}
