package com.raglaw.agentscope.routing;

import com.raglaw.agentscope.workflow.WorkflowDefinition;
import com.raglaw.agentscope.workflow.WorkflowNodeDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowCatalogTest {
    @Test
    void standardCatalogContainsTheLaborReviewDag() {
        WorkflowDefinition definition = WorkflowCatalog.standard().match(classification(TaskType.DISPUTE_ANALYSIS)).orElseThrow();
        assertEquals("LABOR_DISPUTE_REVIEW", definition.code());
        assertEquals(Set.of(TaskType.DISPUTE_ANALYSIS), definition.supportedTasks());
        assertEquals(Set.of("FACTS", "STATUTE", "CASE", "EVIDENCE", "SYNTHESIS"),
                definition.nodes().stream().map(WorkflowNodeDefinition::code).collect(java.util.stream.Collectors.toSet()));
        assertEquals(List.of(), node(definition, "FACTS").dependsOn());
        assertEquals(List.of("FACTS"), node(definition, "STATUTE").dependsOn());
        assertEquals(List.of("FACTS"), node(definition, "CASE").dependsOn());
        assertEquals(List.of("STATUTE", "CASE"), node(definition, "EVIDENCE").dependsOn());
        assertEquals(List.of("EVIDENCE"), node(definition, "SYNTHESIS").dependsOn());
    }

    @Test
    void standardCatalogDoesNotMatchUnrelatedTask() {
        assertTrue(WorkflowCatalog.standard().match(classification(TaskType.CONTRACT_REVIEW)).isEmpty());
    }

    @Test
    void definitionsRejectInvalidGraphsAndExposeImmutableCollections() {
        assertThrows(IllegalArgumentException.class, () -> new WorkflowNodeDefinition(" ", "ROLE", List.of(), false));
        assertThrows(IllegalArgumentException.class, () -> new WorkflowDefinition("W", Set.of(TaskType.DISPUTE_ANALYSIS), Set.of(),
                List.of(new WorkflowNodeDefinition("A", "ROLE", List.of("B"), false))));
        assertThrows(IllegalArgumentException.class, () -> new WorkflowDefinition("W", Set.of(TaskType.DISPUTE_ANALYSIS), Set.of(),
                List.of(new WorkflowNodeDefinition("A", "ROLE", List.of("B"), false), new WorkflowNodeDefinition("B", "ROLE", List.of("A"), false))));
        assertThrows(IllegalArgumentException.class, () -> new WorkflowDefinition("W", Set.of(TaskType.DISPUTE_ANALYSIS), Set.of(),
                List.of(new WorkflowNodeDefinition("A", "ROLE", List.of(), false), new WorkflowNodeDefinition("A", "ROLE2", List.of(), false))));
        WorkflowDefinition definition = new WorkflowDefinition("W", null, null, null);
        assertTrue(definition.supportedTasks().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> definition.nodes().add(new WorkflowNodeDefinition("A", "R", List.of(), false)));
    }

    private static WorkflowNodeDefinition node(WorkflowDefinition definition, String code) {
        return definition.nodes().stream().filter(node -> node.code().equals(code)).findFirst().orElseThrow();
    }

    private static TaskClassification classification(TaskType type) {
        return new TaskClassification(type, Set.of(), 1.0, List.of(), "fictional", "test", "test");
    }
}
