package com.raglaw.agentscope.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentscope.config.ContextMode;
import com.raglaw.agentscope.config.ContextProperties;
import com.raglaw.memory.context.ContextAssembler;
import com.raglaw.memory.context.ContextBudgetPolicy;
import com.raglaw.memory.context.ContextCompactionService;
import com.raglaw.memory.context.ContextItem;
import com.raglaw.memory.context.ContextPriority;
import com.raglaw.memory.context.ContextProfileCatalog;
import com.raglaw.memory.context.ContextSectionType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentContextIntegrationTest {
    @Test
    void offModePreservesLegacyPromptByteForByte() {
        ContextProperties properties = properties(ContextMode.OFF, 1_000);
        AgentContextRenderer renderer = renderer(properties);
        var result = renderer.render(new AgentContextRenderer.RenderRequest("trace", "conversation", "SINGLE_ADVISOR", 3,
                "legacy prompt\nwith exact spacing", List.of(item("fact", ContextSectionType.CASE_FACTS, ContextPriority.P1_HIGH, 10))));
        assertThat(result.prompt()).isEqualTo("legacy prompt\nwith exact spacing");
        assertThat(result.mode()).isEqualTo(ContextMode.OFF);
    }

    @Test
    void shadowModeComputesCandidateButDoesNotChangeResponse() {
        AgentContextRenderer renderer = renderer(properties(ContextMode.SHADOW, 1_000));
        var result = renderer.render(new AgentContextRenderer.RenderRequest("trace", "conversation", "SINGLE_ADVISOR", 3,
                "legacy", List.of(item("task", ContextSectionType.CURRENT_TASK, ContextPriority.P0_REQUIRED, 8))));
        assertThat(result.prompt()).isEqualTo("legacy");
        assertThat(result.includedIds()).contains("task");
    }

    @Test
    void enforceSeparatesTrustedContractsFromUntrustedData() {
        AgentContextRenderer renderer = renderer(properties(ContextMode.ENFORCE, 1_000));
        var result = renderer.render(new AgentContextRenderer.RenderRequest("trace", "conversation", "LEGAL_ANALYSIS", 3,
                "legacy", List.of(
                        item("role", ContextSectionType.ROLE_CONTRACT, ContextPriority.P0_REQUIRED, 8),
                        item("evidence", ContextSectionType.EVIDENCE, ContextPriority.P1_HIGH, 8))));
        assertThat(result.prompt()).contains("[TRUSTED_CONTRACT ROLE_CONTRACT id=role]")
                .contains("[UNTRUSTED_DATA EVIDENCE id=evidence]");
    }

    @Test
    void enforceFailsWhenRequiredContextCannotFitBudget() {
        AgentContextRenderer renderer = renderer(properties(ContextMode.ENFORCE, 10));
        assertThatThrownBy(() -> renderer.render(new AgentContextRenderer.RenderRequest("trace", "conversation", "LEGAL_ANALYSIS", 3,
                "legacy", List.of(item("required", ContextSectionType.CURRENT_TASK, ContextPriority.P0_REQUIRED, 100)))))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void enforceRejectsUnknownProfileInsteadOfUsingSingleAdvisorDefaults() {
        AgentContextRenderer renderer = renderer(properties(ContextMode.ENFORCE, 1_000));
        assertThatThrownBy(() -> renderer.render(new AgentContextRenderer.RenderRequest("trace", "conversation", "UNKNOWN", 3,
                "legacy", List.of(item("task", ContextSectionType.CURRENT_TASK, ContextPriority.P0_REQUIRED, 8)))))
                .hasMessageContaining("UNKNOWN_CONTEXT_PROFILE");
    }

    private static ContextProperties properties(ContextMode mode, int window) {
        ContextProperties properties = new ContextProperties();
        properties.setMode(mode);
        properties.setModelWindowTokens(window);
        return properties;
    }

    private static AgentContextRenderer renderer(ContextProperties properties) {
        return new AgentContextRenderer(properties, new ContextProfileCatalog(),
                new ContextAssembler(new ContextBudgetPolicy()),
                new ContextCompactionService(Optional.empty()), new ContextTraceRecorder(), new ObjectMapper());
    }

    private static ContextItem item(String id, ContextSectionType type, ContextPriority priority, int tokens) {
        return new ContextItem(id, type, priority, id + " content", tokens, false, List.of("source-" + id));
    }
}
