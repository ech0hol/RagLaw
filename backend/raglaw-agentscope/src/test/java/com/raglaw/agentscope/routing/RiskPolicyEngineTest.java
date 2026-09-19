package com.raglaw.agentscope.routing;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RiskPolicyEngineTest {
    private final RiskPolicyEngine engine = new DeterministicRiskPolicyEngine();

    @Test
    void hardHighSignalsRequireHumanReview() {
        for (RiskSignal signal : Set.of(RiskSignal.IMMINENT_DEADLINE, RiskSignal.CRIMINAL_EXPOSURE,
                RiskSignal.EXTERNAL_ACTION_REQUEST)) {
            RouteDecision decision = decide(Set.of(signal), TaskType.GENERAL_CONSULTATION, 1.0, "low risk");
            assertEquals(RiskLevel.HIGH, decision.riskLevel());
            assertEquals(ExecutionMode.HUMAN_REVIEW, decision.executionMode());
            assertTrue(decision.humanApprovalRequired());
        }
    }

    @Test
    void multiIssueDisputeUsesBoundedWorkflow() {
        RouteDecision decision = decide(Set.of(RiskSignal.MULTI_ISSUE_ANALYSIS), TaskType.DISPUTE_ANALYSIS, .8, "multiple issues");

        assertEquals(RiskLevel.MEDIUM, decision.riskLevel());
        assertEquals(ExecutionMode.MULTI_AGENT_WORKFLOW, decision.executionMode());
        assertFalse(decision.humanApprovalRequired());
        assertEquals("LABOR_DISPUTE_REVIEW", decision.workflowCode());
    }

    @Test
    void ordinaryConsultationUsesSingleAgent() {
        RouteDecision decision = decide(Set.of(), TaskType.GENERAL_CONSULTATION, .9, "ordinary question");

        assertEquals(RiskLevel.LOW, decision.riskLevel());
        assertEquals(ExecutionMode.SINGLE_AGENT, decision.executionMode());
        assertEquals("GENERAL_LEGAL", decision.expertRole());
    }

    @Test
    void hardSignalWinsOverClassifierConfidenceAndRationale() {
        RouteDecision decision = decide(Set.of(RiskSignal.IMMINENT_DEADLINE), TaskType.GENERAL_CONSULTATION, 1.0, "low risk");

        assertEquals(RiskLevel.HIGH, decision.riskLevel());
        assertEquals(ExecutionMode.HUMAN_REVIEW, decision.executionMode());
        assertTrue(decision.policyReasons().stream().anyMatch(reason -> reason.contains("IMMINENT_DEADLINE")));
    }

    @Test
    void classifierReasonsUseStablePrecedenceAndPreserveProvenance() {
        RoutingRequest request = new RoutingRequest("tenant-fictional", "user-fictional", "case-fictional", "conversation-fictional", "fictional query", true, false);
        RulePrecheckResult precheck = new RulePrecheckResult(Set.of(), null, null);
        TaskClassification classification = new TaskClassification(TaskType.DISPUTE_ANALYSIS,
                Set.of(RiskSignal.MULTI_ISSUE_ANALYSIS, RiskSignal.CRIMINAL_EXPOSURE, RiskSignal.EXTERNAL_ACTION_REQUEST),
                .8, java.util.List.of("fictional contract"), "three fictional issues", "test", "test");
        RouteDecision decision = engine.decide(request, precheck, classification);

        assertEquals(java.util.List.of("classifier signal: EXTERNAL_ACTION_REQUEST", "classifier signal: CRIMINAL_EXPOSURE",
                "classifier signal: MULTI_ISSUE_ANALYSIS", "classifier rationale: three fictional issues",
                "classifier missing material: fictional contract"), decision.policyReasons());
        assertEquals(ExecutionMode.HUMAN_REVIEW, decision.executionMode());
    }

    @Test
    void externalActionPrecedesDeadlineAndCriminalExposure() {
        RouteDecision decision = decide(Set.of(RiskSignal.EXTERNAL_ACTION_REQUEST, RiskSignal.IMMINENT_DEADLINE,
                RiskSignal.CRIMINAL_EXPOSURE), TaskType.GENERAL_CONSULTATION, .5, "fictional");
        assertEquals(RiskLevel.HIGH, decision.riskLevel());
        assertEquals(ExecutionMode.HUMAN_REVIEW, decision.executionMode());
        assertTrue(decision.policyReasons().get(0).contains("EXTERNAL_ACTION_REQUEST"));
    }

    private RouteDecision decide(Set<RiskSignal> signals, TaskType type, double confidence, String rationale) {
        RoutingRequest request = new RoutingRequest("tenant-fictional", "user-fictional", "case-fictional", "conversation-fictional", "fictional query", true, false);
        RulePrecheckResult precheck = new RulePrecheckResult(signals, null, null);
        TaskClassification classification = new TaskClassification(type, Set.of(), confidence, null, rationale, "test", "test");
        return engine.decide(request, precheck, classification);
    }
}
