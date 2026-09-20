package com.raglaw.agentscope.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.memory.casefile.CaseScope;
import com.raglaw.memory.service.CaseMemorySnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentScopeWorkflowNodeRunnerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkflowContextProjector projector = new WorkflowContextProjector();

    @Test
    void usesFrozenExpertVersionAndToolsAndProjectsDependencyResults() throws Exception {
        ResolvedWorkflowNode dependency = new ResolvedWorkflowNode(
                "fact", "FACT", "fact-expert", 1, Set.of("rag_search"), "fact-schema", List.of());
        ResolvedWorkflowNode statute = new ResolvedWorkflowNode(
                "statute", "STATUTE", "statute-expert", 2, Set.of("rag_search"), "statute-schema", List.of("fact"));
        WorkflowExecutionManifest manifest = manifest(Map.of("fact", dependency, "statute", statute));
        NodeExecutionResult factResult = new NodeExecutionResult(
                "fact", "fact-key", "SUCCEEDED", Map.of("date", "2025-01-01"), List.of("fact-chunk"), 18);
        SharedWorkflowState state = state(manifest, Map.of("fact", factResult));

        WorkflowAgentInvoker invoker = (resolved, view, prompt, context) -> {
            assertThat(resolved.agentCode()).isEqualTo("statute-expert");
            assertThat(resolved.agentVersion()).isEqualTo(2);
            assertThat(resolved.effectiveTools()).containsExactly("rag_search");
            assertThat(view.dependencyResults()).containsKey("fact");
            assertThat(prompt).contains("statute-expert", "fact-chunk", "statute-schema");
            return new AgentInvocationResult("statute answer", List.of("statute-chunk"), Map.of("role", resolved.roleCode()));
        };

        WorkflowNodeRunner runner = new WorkflowNodeRunnerFactory(projector, invoker, objectMapper)
                .create(manifest, state);
        WorkflowNodeResult result = runner.run(
                new WorkflowNodeDefinition("statute", "STATUTE", List.of("fact"), false),
                new WorkflowExecutionContext("run-1", "trace-1", "analyze", Map.of()));

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.structuredOutputJson()).contains("statute answer", "statute-expert", "2");
        assertThat(result.evidenceIds()).containsExactly("statute-chunk");
    }

    @Test
    void parallelNodesKeepTheirOwnFrozenBindings() throws Exception {
        ResolvedWorkflowNode statute = new ResolvedWorkflowNode(
                "statute", "STATUTE", "statute-expert", 3, Set.of("rag_search"), "", List.of());
        ResolvedWorkflowNode caseLaw = new ResolvedWorkflowNode(
                "case-law", "CASE_LAW", "case-expert", 4, Set.of("history_lookup"), "", List.of());
        WorkflowExecutionManifest manifest = manifest(Map.of("statute", statute, "case-law", caseLaw));
        SharedWorkflowState state = state(manifest, Map.of());
        CountDownLatch bothEntered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        Map<String, String> bindings = new ConcurrentHashMap<>();
        WorkflowAgentInvoker invoker = (resolved, view, prompt, context) -> {
            bindings.put(resolved.agentCode(), resolved.agentVersion() + ":" + String.join(",", resolved.effectiveTools()));
            bothEntered.countDown();
            assertThat(bothEntered.await(2, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            return new AgentInvocationResult("ok", List.of(), Map.of());
        };

        WorkflowNodeRunner runner = new WorkflowNodeRunnerFactory(projector, invoker, objectMapper)
                .create(manifest, state);
        WorkflowExecutor.ExecutionResult result = new WorkflowExecutor(2, java.time.Duration.ofSeconds(3), 1)
                .execute(
                        new WorkflowDefinition("parallel", Set.of(), Set.of(), List.of(
                                new WorkflowNodeDefinition("statute", "STATUTE", List.of(), true),
                                new WorkflowNodeDefinition("case-law", "CASE_LAW", List.of(), true))),
                        "run-2", "trace-2", "research", runner);

        assertThat(result.status()).isEqualTo(WorkflowExecutor.Status.SUCCEEDED);
        assertThat(bindings).containsEntry("statute-expert", "3:rag_search");
        assertThat(bindings).containsEntry("case-expert", "4:history_lookup");
    }

    @Test
    void factoryRejectsStateFromAnotherManifest() {
        ResolvedWorkflowNode node = new ResolvedWorkflowNode(
                "fact", "FACT", "fact-expert", 1, Set.of("rag_search"), "", List.of());
        WorkflowExecutionManifest first = manifest(Map.of("fact", node));
        WorkflowExecutionManifest second = new WorkflowExecutionManifest(
                "different-run", first.tenantId(), first.userId(), first.caseId(), first.conversationId(),
                first.workflowCode(), first.workflowVersion(), first.routeDecisionId(), first.memorySnapshotVersion(),
                first.riskPolicyVersion(), first.toolPolicyVersion(), first.nodes(), first.manifestChecksum());

        assertThatThrownBy(() -> new WorkflowNodeRunnerFactory(projector,
                (resolved, view, prompt, context) -> new AgentInvocationResult("ok", List.of(), Map.of()), objectMapper)
                .create(first, state(second, Map.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same execution");
    }

    private WorkflowExecutionManifest manifest(Map<String, ResolvedWorkflowNode> nodes) {
        return new WorkflowExecutionManifest(
                "run-1", "tenant-1", "user-1", "case-1", "conversation-1", "LABOR_DISPUTE_REVIEW", 1,
                "route-1", 18, "risk-v1", "tools-v1", nodes, "checksum");
    }

    private SharedWorkflowState state(WorkflowExecutionManifest manifest, Map<String, NodeExecutionResult> completed) {
        return new SharedWorkflowState(
                manifest,
                new CaseMemorySnapshot(new CaseScope("tenant-1", "user-1", "case-1"), 18, Instant.parse("2025-01-01T00:00:00Z")),
                new WorkflowTaskNote(1, Map.of("objective", List.of("research"))),
                List.of(new WorkflowCaseFact("memory-1", "EMPLOYMENT_DATE", "2025-01-01", "CONFIRMED", "SHARED", List.of("msg-1"))),
                List.of(new WorkflowEvidenceReference("doc-1", "fact-chunk", 1, "合同条款", "sha256")),
                completed);
    }
}
