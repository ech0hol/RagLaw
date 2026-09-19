package com.raglaw.server.routing;

import com.raglaw.agentscope.config.RoutingProperties;
import com.raglaw.agentscope.domain.*;
import com.raglaw.agentscope.routing.*;
import com.raglaw.agentscope.workflow.*;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HumanGatedWorkflowIT {
    private static RouteDecision high(RiskLevel level) { return new RouteDecision(TaskType.DISPUTE_ANALYSIS, level, ExecutionMode.MULTI_AGENT_WORKFLOW, "role", "LABOR_DISPUTE_REVIEW", List.of("fictional-risk"), true, "policy-fictional"); }
    @Test void highRiskRequiresApprovalBeforeRunner() {
        var runs = mock(WorkflowRunRepository.class); var nodes = mock(WorkflowNodeRunRepository.class); var props = new RoutingProperties(); props.setMode(com.raglaw.agentscope.config.RoutingMode.ENFORCE);
        var service = new WorkflowRunService(new WorkflowExecutor(), runs, nodes, props); var runner = mock(WorkflowNodeRunner.class);
        when(runs.save(any())).thenAnswer(i -> i.getArgument(0));
        var outcome = service.route(high(RiskLevel.HIGH), "trace-fictional", "fictional input", WorkflowCatalog.standard().match(new TaskClassification(TaskType.DISPUTE_ANALYSIS, Set.of(), 1, List.of(), "x", "x", "x")).orElseThrow(), runner);
        assertEquals("approval_required", outcome.status()); assertTrue(outcome.approvalRequired()); verifyNoInteractions(runner); verify(runs).save(any(WorkflowRunEntity.class));
    }
    @Test void approvalCreatesNewRunAndRejectionIsTerminal() {
        var runs = mock(WorkflowRunRepository.class); var nodes = mock(WorkflowNodeRunRepository.class); var props = new RoutingProperties(); props.setMode(com.raglaw.agentscope.config.RoutingMode.ENFORCE);
        var service = new WorkflowRunService(new WorkflowExecutor(), runs, nodes, props); var prior = new WorkflowRunEntity(); prior.setId("run-prior"); prior.setRouteDecisionId("route-decision-fictional"); when(runs.findById("run-prior")).thenReturn(Optional.of(prior)); when(runs.save(any())).thenAnswer(i -> i.getArgument(0));
        var wf = new WorkflowDefinition("FICTIONAL", Set.of(TaskType.DISPUTE_ANALYSIS), Set.of(), List.of(new WorkflowNodeDefinition("FACTS", "FACT", List.of(), false)));
        var approved = service.approve("run-prior", "trace-fictional", high(RiskLevel.HIGH), wf, "fictional", (n,c) -> new WorkflowNodeResult(n.code(), "SUCCEEDED", "{}", List.of(), 1));
        assertEquals("SUCCEEDED", approved.status()); assertNotEquals("run-prior", approved.runId());
        var savedRuns = org.mockito.ArgumentCaptor.forClass(WorkflowRunEntity.class);
        verify(runs, atLeastOnce()).save(savedRuns.capture());
        assertTrue(savedRuns.getAllValues().stream().anyMatch(value -> "route-decision-fictional".equals(value.getRouteDecisionId())));
        var rejectionRun = new WorkflowRunEntity(); rejectionRun.setId("run-reject"); when(runs.findById("run-reject")).thenReturn(Optional.of(rejectionRun));
        assertEquals("REJECTED", service.reject("run-reject", "fictional reviewer rejected").status()); assertEquals("REJECTED", rejectionRun.getStatus());
    }
    @Test void migrationAndDefaultModeArePresent() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("db/migration/V40__workflow_runtime.sql")) {
            assertNotNull(in); String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8).toLowerCase(); assertTrue(sql.contains("raglaw_workflow_run")); assertTrue(sql.contains("raglaw_workflow_node_run")); assertTrue(sql.contains("idx_workflow_run_trace"));
        }
        assertEquals(com.raglaw.agentscope.config.RoutingMode.SHADOW, new RoutingProperties().getMode());
    }
}
