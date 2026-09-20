package com.raglaw.memory.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContextAssemblerTest {
    @Test
    void keepsCriticalFactsAndDropsOptionalNoiseFirst() {
        NodeContextProfile profile = new ContextProfileCatalog().require("LEGAL_ANALYSIS");
        ContextRequest request = new ContextRequest("LEGAL_ANALYSIS", 18, profile, new ModelWindow(100), List.of(
                new ContextItem("debug-log", ContextSectionType.TOOL_ARTIFACT, ContextPriority.P3_OPTIONAL, "debug", 50, true, List.of()),
                new ContextItem("current-task", ContextSectionType.CURRENT_TASK, ContextPriority.P0_REQUIRED, "task", 10, false, List.of()),
                new ContextItem("confirmed-start-date", ContextSectionType.CASE_FACTS, ContextPriority.P1_HIGH, "date", 20, false, List.of("m1")),
                new ContextItem("evidence-clause-7", ContextSectionType.EVIDENCE, ContextPriority.P1_HIGH, "clause", 20, false, List.of("doc-1"))));
        AssembledContext result = new ContextAssembler(new ContextBudgetPolicy()).assemble(request);
        assertThat(result.includedIds()).contains("current-task", "confirmed-start-date", "evidence-clause-7");
        assertThat(result.omittedIds()).contains("debug-log");
    }

    @Test
    void neverSilentlyDropsP0() {
        NodeContextProfile profile = new ContextProfileCatalog().require("LEGAL_ANALYSIS");
        ContextRequest request = new ContextRequest("LEGAL_ANALYSIS", 18, profile, new ModelWindow(10), List.of(
                new ContextItem("task", ContextSectionType.CURRENT_TASK, ContextPriority.P0_REQUIRED, "large", 20, false, List.of())));
        assertThatThrownBy(() -> new ContextAssembler(new ContextBudgetPolicy()).assemble(request)).isInstanceOf(CriticalContextOverflowException.class);
    }
}
