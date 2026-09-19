package com.raglaw.agentscope.routing;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TaskRoutingServiceTest {
    private final RoutingRequest request = new RoutingRequest("tenant", "user", "case", "conversation", "fictional dispute", true, false);
    private final RulePrecheckResult precheck = new RulePrecheckResult(Set.of(), List.of(), List.of("fictional"));
    private final TaskClassification classification = new TaskClassification(TaskType.DISPUTE_ANALYSIS, Set.of(RiskSignal.MULTI_ISSUE_ANALYSIS), 1.0, List.of(), "fictional", "p", "m");

    @Test
    void composesEachUpstreamOnceAndCatalogOnlyForWorkflow() {
        AtomicInteger precheckCalls = new AtomicInteger();
        AtomicInteger classifierCalls = new AtomicInteger();
        AtomicInteger policyCalls = new AtomicInteger();
        AtomicInteger catalogCalls = new AtomicInteger();
        WorkflowCatalog catalog = c -> { catalogCalls.incrementAndGet(); return Optional.empty(); };
        TaskRoutingService service = new TaskRoutingService(r -> { precheckCalls.incrementAndGet(); return precheck; },
                r -> { classifierCalls.incrementAndGet(); return classification; },
                (r, p, c) -> { policyCalls.incrementAndGet(); return new RouteDecision(TaskType.DISPUTE_ANALYSIS, RiskLevel.MEDIUM, ExecutionMode.MULTI_AGENT_WORKFLOW, "DISPUTE_ANALYSIS", "LABOR_DISPUTE_REVIEW", List.of("policy"), false, "v1"); }, catalog);
        RouteDecision decision = service.route(request);
        assertEquals(ExecutionMode.HUMAN_REVIEW, decision.executionMode());
        assertTrue(decision.humanApprovalRequired());
        assertTrue(decision.policyReasons().get(0).startsWith("workflow_clarification:"));
        assertEquals(1, precheckCalls.get());
        assertEquals(1, classifierCalls.get());
        assertEquals(1, policyCalls.get());
        assertEquals(1, catalogCalls.get());
    }

    @Test
    void missingMaterialsFailClosed() {
        WorkflowCatalog catalog = c -> Optional.of(new com.raglaw.agentscope.workflow.WorkflowDefinition("W", Set.of(TaskType.DISPUTE_ANALYSIS), Set.of("labor contract"), List.of()));
        TaskClassification missing = new TaskClassification(TaskType.DISPUTE_ANALYSIS, Set.of(), 1.0, List.of("labor contract"), "fictional", "p", "m");
        TaskRoutingService service = new TaskRoutingService(r -> precheck, r -> missing,
                (r, p, c) -> new RouteDecision(c.taskType(), RiskLevel.MEDIUM, ExecutionMode.MULTI_AGENT_WORKFLOW, "ROLE", "W", List.of("policy"), false, "v1"), catalog);
        RouteDecision decision = service.route(request);
        assertEquals(ExecutionMode.HUMAN_REVIEW, decision.executionMode());
        assertTrue(decision.humanApprovalRequired());
        assertTrue(decision.policyReasons().get(0).startsWith("workflow_clarification:"));
    }

    @Test
    void singleAgentAndHumanReviewPreservePolicyAndBypassCatalog() {
        AtomicInteger catalogCalls = new AtomicInteger();
        WorkflowCatalog catalog = c -> { catalogCalls.incrementAndGet(); return Optional.empty(); };
        for (ExecutionMode mode : List.of(ExecutionMode.SINGLE_AGENT, ExecutionMode.HUMAN_REVIEW)) {
            RouteDecision expected = new RouteDecision(TaskType.STATUTE_LOOKUP, RiskLevel.LOW, mode, "ROLE", null, List.of("policy"), mode == ExecutionMode.HUMAN_REVIEW, "v1");
            TaskRoutingService service = new TaskRoutingService(r -> precheck, r -> new TaskClassification(TaskType.STATUTE_LOOKUP, Set.of(), 1, List.of(), "", "", ""),
                    (r, p, c) -> expected, catalog);
            assertEquals(expected, service.route(request));
        }
        assertEquals(0, catalogCalls.get());
    }
}
