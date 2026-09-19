package com.raglaw.agentscope.workflow;

import com.raglaw.agentscope.routing.TaskType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record WorkflowDefinition(
        String code,
        Set<TaskType> supportedTasks,
        Set<String> requiredMaterials,
        List<WorkflowNodeDefinition> nodes
) {
    public WorkflowDefinition {
        requireText(code, "code");
        supportedTasks = supportedTasks == null ? Set.of() : Set.copyOf(supportedTasks);
        requiredMaterials = requiredMaterials == null ? Set.of() : Set.copyOf(requiredMaterials);
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        validateNodes(nodes);
    }

    private static void validateNodes(List<WorkflowNodeDefinition> nodes) {
        Map<String, WorkflowNodeDefinition> byCode = new HashMap<>();
        for (WorkflowNodeDefinition node : nodes) {
            if (byCode.put(node.code(), node) != null) throw new IllegalArgumentException("duplicate node code: " + node.code());
        }
        for (WorkflowNodeDefinition node : nodes) {
            for (String dependency : node.dependsOn()) {
                if (!byCode.containsKey(dependency)) throw new IllegalArgumentException("unknown dependency: " + dependency);
            }
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String code : byCode.keySet()) detectCycle(code, byCode, visiting, visited);
    }

    private static void detectCycle(String code, Map<String, WorkflowNodeDefinition> byCode,
                                    Set<String> visiting, Set<String> visited) {
        if (visited.contains(code)) return;
        if (!visiting.add(code)) throw new IllegalArgumentException("workflow graph contains a cycle");
        for (String dependency : byCode.get(code).dependsOn()) detectCycle(dependency, byCode, visiting, visited);
        visiting.remove(code);
        visited.add(code);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
