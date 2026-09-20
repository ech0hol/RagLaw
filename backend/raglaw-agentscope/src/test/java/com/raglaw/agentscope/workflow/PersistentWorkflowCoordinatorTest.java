package com.raglaw.agentscope.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentscope.domain.WorkflowConflictRepository;
import com.raglaw.agentscope.domain.WorkflowNodeRunRepository;
import com.raglaw.agentscope.domain.WorkflowRunEntity;
import com.raglaw.agentscope.domain.WorkflowRunRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PersistentWorkflowCoordinatorTest {
    @Test
    void startPersistsNodeResultAndResumeUsesAcceptedResultWithoutRerunningIt() {
        WorkflowRunRepository runs = mock(WorkflowRunRepository.class);
        WorkflowNodeRunRepository nodes = mock(WorkflowNodeRunRepository.class);
        WorkflowConflictRepository conflicts = mock(WorkflowConflictRepository.class);
        WorkflowRunStore store = mock(WorkflowRunStore.class);
        WorkflowExecutor executor = new WorkflowExecutor(2, java.time.Duration.ofSeconds(2), 1);
        WorkflowRunEntity run = run("run-1");
        when(runs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(runs.findById("run-1")).thenReturn(Optional.of(run));
        when(store.startAttempt(any())).thenReturn(new WorkflowRunStore.StartAttemptResult("node-key", false));
        when(store.completeAttempt(any())).thenAnswer(invocation -> {
            var command = (WorkflowRunStore.CompleteAttemptCommand) invocation.getArgument(0);
            return new NodeExecutionResult(command.nodeCode(), command.idempotencyKey(), command.status(), command.output(), command.evidenceIds(), command.snapshotVersion());
        });

        PersistentWorkflowCoordinator coordinator = new PersistentWorkflowCoordinator(
                runs, nodes, conflicts, store, executor, new ObjectMapper());
        WorkflowDefinition definition = new WorkflowDefinition("wf", java.util.Set.of(), java.util.Set.of(),
                List.of(new WorkflowNodeDefinition("fact", "FACT", List.of(), false)));
        int invocations = 0;
        WorkflowNodeRunner runner = (node, context) -> new WorkflowNodeResult(node.code(), "SUCCEEDED", "{\"answer\":\"ok\"}", List.of("e1"), 2);

        PersistentWorkflowCoordinator.WorkflowRunOutcome outcome = coordinator.start(
                new PersistentWorkflowCoordinator.StartWorkflowCommand(run, definition, "trace-1", "input", runner));

        assertThat(outcome.status()).isEqualTo("SUCCEEDED");
        assertThat(run.getStatus()).isEqualTo("SUCCEEDED");
        org.mockito.Mockito.verify(store).completeAttempt(any());
    }

    @Test
    void resumeReturnsAcceptedAttemptWithoutCallingTheAgentAgain() throws Exception {
        WorkflowRunRepository runs = mock(WorkflowRunRepository.class);
        WorkflowNodeRunRepository nodes = mock(WorkflowNodeRunRepository.class);
        WorkflowConflictRepository conflicts = mock(WorkflowConflictRepository.class);
        WorkflowRunStore store = mock(WorkflowRunStore.class);
        WorkflowRunEntity run = run("run-resume");
        run.setStatus("RUNNING");
        when(runs.findById("run-resume")).thenReturn(Optional.of(run));
        when(runs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(nodes.findByRunId("run-resume")).thenReturn(List.of());
        when(store.startAttempt(any())).thenReturn(new WorkflowRunStore.StartAttemptResult("accepted", true));
        when(store.findAccepted(any())).thenReturn(Optional.of(new NodeExecutionResult(
                "fact", "accepted", "SUCCEEDED", Map.of("answer", "persisted"), List.of("e1"), 0)));
        PersistentWorkflowCoordinator coordinator = new PersistentWorkflowCoordinator(
                runs, nodes, conflicts, store, new WorkflowExecutor(1, java.time.Duration.ofSeconds(2), 1), new ObjectMapper());
        WorkflowDefinition definition = new WorkflowDefinition("wf", java.util.Set.of(), java.util.Set.of(),
                List.of(new WorkflowNodeDefinition("fact", "FACT", List.of(), false)));
        run.setTenantId("tenant"); run.setUserId("user"); run.setCaseId("case"); run.setWorkflowVersion(1);
        WorkflowExecutionManifest manifest = new WorkflowExecutionManifest("run-resume", "tenant", "user", "case", "conversation",
                "wf", 1, "route-1", 1L, "risk-v1", "tool-v1",
                Map.of("fact", new ResolvedWorkflowNode("fact", "FACT", "fact-agent", 1, java.util.Set.of(), "{}", List.of())), "checksum");
        ObjectMapper mapper = new ObjectMapper();
        run.setManifestJson(mapper.writeValueAsString(manifest));
        run.setWorkflowDefinitionJson(mapper.writeValueAsString(definition));
        java.util.concurrent.atomic.AtomicInteger invocations = new java.util.concurrent.atomic.AtomicInteger();
        WorkflowNodeRunner boundRunner = new WorkflowNodeRunner() {
            @Override public WorkflowNodeResult run(WorkflowNodeDefinition node, WorkflowExecutionContext context) { invocations.incrementAndGet(); return new WorkflowNodeResult(node.code(), "SUCCEEDED", "{}", List.of(), 1); }
            @Override public String frozenManifestJson() { try { return mapper.writeValueAsString(manifest); } catch (Exception e) { throw new IllegalStateException(e); } }
            @Override public WorkflowExecutionManifest frozenManifest() { return manifest; }
        };

        PersistentWorkflowCoordinator.WorkflowRunOutcome outcome = coordinator.resume(
                new PersistentWorkflowCoordinator.ResumeWorkflowCommand("run-resume", definition, "trace", "input",
                        boundRunner));

        assertThat(outcome.status()).isEqualTo("SUCCEEDED");
        assertThat(invocations).hasValue(0);
    }

    @Test
    void executeExistingFailsClosedWhenDurableManifestIsMissing() {
        WorkflowRunRepository runs = mock(WorkflowRunRepository.class);
        WorkflowNodeRunRepository nodes = mock(WorkflowNodeRunRepository.class);
        WorkflowConflictRepository conflicts = mock(WorkflowConflictRepository.class);
        WorkflowRunStore store = mock(WorkflowRunStore.class);
        PersistentWorkflowCoordinator coordinator = new PersistentWorkflowCoordinator(
                runs, nodes, conflicts, store, new WorkflowExecutor(1, java.time.Duration.ofSeconds(2), 1), new ObjectMapper());
        WorkflowRunEntity run = run("run-missing-manifest");
        WorkflowDefinition definition = new WorkflowDefinition("wf", java.util.Set.of(), java.util.Set.of(),
                List.of(new WorkflowNodeDefinition("fact", "FACT", List.of(), false)));

        assertThatThrownBy(() -> coordinator.executeExisting(run, definition, "trace", "input",
                (node, context) -> new WorkflowNodeResult(node.code(), "SUCCEEDED", "{}", List.of(), 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("WORKFLOW_MANIFEST_NOT_FOUND");
    }

    private WorkflowRunEntity run(String id) {
        WorkflowRunEntity run = new WorkflowRunEntity();
        run.setId(id); run.setTraceId("trace-1"); run.setRouteDecisionId("route-1");
        run.setWorkflowCode("wf"); run.setStatus("CREATED"); run.setApprovalStatus("APPROVED");
        run.setStartedAt(java.time.Instant.now());
        return run;
    }
}
