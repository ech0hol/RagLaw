package com.raglaw.agentscope.routing;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class RulePrecheckTest {
    private final RulePrecheck precheck = new DeterministicRulePrecheck();

    @Test
    void detectsAllHardSignalsFromRequest() {
        RoutingRequest request = request("明天是仲裁最后期限，请帮我直接给对方发送律师函");

        RulePrecheckResult result = precheck.evaluate(request);

        assertEquals(new HashSet<>(java.util.Set.of(
                RiskSignal.IMMINENT_DEADLINE, RiskSignal.EXTERNAL_ACTION_REQUEST)), result.hardSignals());
        assertEquals(2, result.reasons().size());
    }

    @Test
    void detectsMissingContractMaterialOnlyForContractReview() {
        RulePrecheckResult result = precheck.evaluate(request("请审查这份劳动合同", false));

        assertTrue(result.hardSignals().contains(RiskSignal.MISSING_CORE_MATERIAL));
        assertTrue(result.missingMaterials().contains("contract document"));
    }

    @Test
    void doesNotTreatOrdinaryInformationAsRisk() {
        RulePrecheckResult result = precheck.evaluate(request("我只是想了解劳动合同法中的试用期"));

        assertTrue(result.hardSignals().isEmpty());
        assertTrue(result.reasons().isEmpty());
    }

    @Test
    void doesNotTreatGenericCriminalOrAmountVocabularyAsRisk() {
        assertTrue(precheck.evaluate(request("我想了解刑事法律和一百万元的法律概念")).hardSignals().isEmpty());
    }

    @Test
    void resultCollectionsAreImmutableAndNullCollectionsNormalizeToEmpty() {
        RulePrecheckResult result = new RulePrecheckResult(null, null, null);

        assertTrue(result.hardSignals().isEmpty());
        assertTrue(result.missingMaterials().isEmpty());
        assertTrue(result.reasons().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> result.hardSignals().add(RiskSignal.IMMINENT_DEADLINE));
        assertThrows(UnsupportedOperationException.class, () -> result.missingMaterials().add("x"));
        assertThrows(UnsupportedOperationException.class, () -> result.reasons().add("x"));
    }

    @Test
    void copiesCallerCollections() {
        var signals = new HashSet<>(java.util.Set.of(RiskSignal.IMMINENT_DEADLINE));
        var missing = new ArrayList<>(java.util.List.of("x"));
        var reasons = new ArrayList<>(java.util.List.of("reason"));
        RulePrecheckResult result = new RulePrecheckResult(signals, missing, reasons);

        signals.clear();
        missing.clear();
        reasons.clear();

        assertEquals(1, result.hardSignals().size());
        assertEquals(1, result.missingMaterials().size());
        assertEquals(1, result.reasons().size());
    }

    private static RoutingRequest request(String query) {
        return request(query, true);
    }

    private static RoutingRequest request(String query, boolean hasDocument) {
        return new RoutingRequest("tenant-fictional", "user-fictional", "case-fictional", "conversation-fictional", query, hasDocument, false);
    }
}
