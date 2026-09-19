package com.raglaw.agentscope.routing;

import com.raglaw.agentscope.workflow.WorkflowDefinition;
import com.raglaw.agentscope.workflow.WorkflowNodeDefinition;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface WorkflowCatalog {
    Optional<WorkflowDefinition> match(TaskClassification classification);

    static WorkflowCatalog standard() {
        return StandardCatalog.INSTANCE;
    }

    final class StandardCatalog implements WorkflowCatalog {
        private static final StandardCatalog INSTANCE = new StandardCatalog();
        private final WorkflowDefinition definition = new WorkflowDefinition(
                "LABOR_DISPUTE_REVIEW", Set.of(TaskType.DISPUTE_ANALYSIS), Set.of(), List.of(
                new WorkflowNodeDefinition("FACTS", "FACT_ANALYST", List.of(), false),
                new WorkflowNodeDefinition("STATUTE", "STATUTE_RESEARCHER", List.of("FACTS"), true),
                new WorkflowNodeDefinition("CASE", "CASE_RESEARCHER", List.of("FACTS"), true),
                new WorkflowNodeDefinition("EVIDENCE", "EVIDENCE_ANALYST", List.of("STATUTE", "CASE"), false),
                new WorkflowNodeDefinition("SYNTHESIS", "LEGAL_SYNTHESIZER", List.of("EVIDENCE"), false)));

        @Override
        public Optional<WorkflowDefinition> match(TaskClassification classification) {
            if (classification == null) return Optional.empty();
            return definition.supportedTasks().contains(classification.taskType()) ? Optional.of(definition) : Optional.empty();
        }
    }
}
