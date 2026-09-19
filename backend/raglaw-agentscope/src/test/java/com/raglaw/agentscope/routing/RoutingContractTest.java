package com.raglaw.agentscope.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoutingContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void jacksonRoundTripsEveryRoutingContract() throws Exception {
        RoutingRequest request = new RoutingRequest(
                "tenant-1", "user-1", "case-1", "conversation-1", "review", true, false);
        TaskClassification classification = new TaskClassification(
                TaskType.CONTRACT_REVIEW,
                Set.of(RiskSignal.RIGHTS_WAIVER, RiskSignal.MISSING_CORE_MATERIAL),
                0.87,
                List.of("signed annex"),
                "Contract review with a possible waiver.",
                "prompt-v1",
                "model-v1");
        RouteDecision decision = new RouteDecision(
                TaskType.CONTRACT_REVIEW,
                RiskLevel.HIGH,
                ExecutionMode.HUMAN_REVIEW,
                "contract-expert",
                "contract-review-v1",
                List.of("rights waiver"),
                true,
                "policy-v1");

        assertEquals(request, objectMapper.readValue(objectMapper.writeValueAsString(request), RoutingRequest.class));
        assertEquals(classification, objectMapper.readValue(
                objectMapper.writeValueAsString(classification), TaskClassification.class));
        assertEquals(decision, objectMapper.readValue(
                objectMapper.writeValueAsString(decision), RouteDecision.class));
        for (TaskType value : TaskType.values()) {
            assertEquals(value, objectMapper.readValue(objectMapper.writeValueAsString(value), TaskType.class));
        }
        for (RiskLevel value : RiskLevel.values()) {
            assertEquals(value, objectMapper.readValue(objectMapper.writeValueAsString(value), RiskLevel.class));
        }
        for (RiskSignal value : RiskSignal.values()) {
            assertEquals(value, objectMapper.readValue(objectMapper.writeValueAsString(value), RiskSignal.class));
        }
        for (ExecutionMode value : ExecutionMode.values()) {
            assertEquals(value, objectMapper.readValue(objectMapper.writeValueAsString(value), ExecutionMode.class));
        }
    }

    @Test
    void blankRequiredIdsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new RoutingRequest(" ", "user", "case", "conversation", "query", false, false));
        assertThrows(IllegalArgumentException.class,
                () -> new RoutingRequest("tenant", "", "case", "conversation", "query", false, false));
        assertThrows(IllegalArgumentException.class,
                () -> new RoutingRequest("tenant", "user", "\t", "conversation", "query", false, false));
        assertThrows(IllegalArgumentException.class,
                () -> new RoutingRequest("tenant", "user", "case", null, "query", false, false));
    }

    @Test
    void confidenceMustBeWithinInclusiveUnitInterval() {
        assertEquals(0.0, new TaskClassification(
                TaskType.GENERAL_CONSULTATION, null, 0.0, null, "rationale", "p", "m").confidence());
        assertEquals(1.0, new TaskClassification(
                TaskType.GENERAL_CONSULTATION, null, 1.0, null, "rationale", "p", "m").confidence());
        assertThrows(IllegalArgumentException.class, () -> new TaskClassification(
                TaskType.GENERAL_CONSULTATION, null, -0.01, null, "rationale", "p", "m"));
        assertThrows(IllegalArgumentException.class, () -> new TaskClassification(
                TaskType.GENERAL_CONSULTATION, null, 1.01, null, "rationale", "p", "m"));
        assertThrows(IllegalArgumentException.class, () -> new TaskClassification(
                TaskType.GENERAL_CONSULTATION, null, Double.NaN, null, "rationale", "p", "m"));
    }

    @Test
    void nullCollectionsBecomeImmutableEmptyCollections() {
        TaskClassification classification = new TaskClassification(
                TaskType.STATUTE_LOOKUP, null, 0.5, null, "rationale", "p", "m");
        RouteDecision decision = new RouteDecision(
                TaskType.STATUTE_LOOKUP, RiskLevel.LOW, ExecutionMode.SINGLE_AGENT,
                "statute-expert", "statute-v1", null, false, "policy-v1");

        assertTrue(classification.riskSignals().isEmpty());
        assertTrue(classification.missingMaterials().isEmpty());
        assertTrue(decision.policyReasons().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> classification.riskSignals().add(RiskSignal.CONFLICTING_FACTS));
        assertThrows(UnsupportedOperationException.class,
                () -> classification.missingMaterials().add("new material"));
        assertThrows(UnsupportedOperationException.class,
                () -> decision.policyReasons().add("new reason"));
    }

    @Test
    void suppliedCollectionsAreDefensivelyCopied() {
        Set<RiskSignal> signals = EnumSet.of(RiskSignal.MULTI_ISSUE_ANALYSIS);
        List<String> missing = new ArrayList<>(List.of("annex"));
        List<String> reasons = new ArrayList<>(List.of("high risk"));
        TaskClassification classification = new TaskClassification(
                TaskType.DISPUTE_ANALYSIS, signals, 0.5, missing, "rationale", "p", "m");
        RouteDecision decision = new RouteDecision(
                TaskType.DISPUTE_ANALYSIS, RiskLevel.MEDIUM, ExecutionMode.MULTI_AGENT_WORKFLOW,
                "dispute-expert", "dispute-v1", reasons, false, "policy-v1");

        signals.clear();
        missing.clear();
        reasons.clear();
        assertEquals(Set.of(RiskSignal.MULTI_ISSUE_ANALYSIS), classification.riskSignals());
        assertEquals(List.of("annex"), classification.missingMaterials());
        assertEquals(List.of("high risk"), decision.policyReasons());
    }
}
