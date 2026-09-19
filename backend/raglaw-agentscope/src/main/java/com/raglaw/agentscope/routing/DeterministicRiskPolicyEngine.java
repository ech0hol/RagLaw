package com.raglaw.agentscope.routing;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.Comparator;
import org.springframework.stereotype.Component;

@Component
public final class DeterministicRiskPolicyEngine implements RiskPolicyEngine {
    public static final String POLICY_VERSION = "risk-policy-v1";

    @Override
    public RouteDecision decide(RoutingRequest request, RulePrecheckResult precheck, TaskClassification classification) {
        if (request == null || precheck == null || classification == null) {
            throw new IllegalArgumentException("request, precheck, and classification are required");
        }
        EnumSet<RiskSignal> signals = EnumSet.noneOf(RiskSignal.class);
        signals.addAll(precheck.hardSignals());
        signals.addAll(classification.riskSignals());
        List<String> reasons = new ArrayList<>(precheck.reasons());
        for (RiskSignal signal : precheck.hardSignals().stream().sorted(Comparator.comparingInt(DeterministicRiskPolicyEngine::precedence)).toList()) {
            if (reasons.stream().noneMatch(reason -> reason.contains(signal.name()))) {
                reasons.add("hard signal " + signal);
            }
        }
        for (RiskSignal signal : classification.riskSignals().stream().sorted(Comparator.comparingInt(DeterministicRiskPolicyEngine::precedence)).toList()) {
            if (!precheck.hardSignals().contains(signal)) {
                reasons.add("classifier signal: " + signal);
            }
        }
        if (classification.rationale() != null && !classification.rationale().isBlank()) {
            reasons.add("classifier rationale: " + classification.rationale());
        }
        for (String material : classification.missingMaterials()) {
            reasons.add("classifier missing material: " + material);
        }
        if (classification.taskType() == TaskType.CONTRACT_REVIEW && !request.hasContractDocument()) {
            signals.add(RiskSignal.MISSING_CORE_MATERIAL);
            reasons.add("hard signal MISSING_CORE_MATERIAL: contract review requires a contract document");
        }

        RiskSignal strongest = strongest(signals);
        if (strongest == RiskSignal.EXTERNAL_ACTION_REQUEST || strongest == RiskSignal.IMMINENT_DEADLINE
                || strongest == RiskSignal.CRIMINAL_EXPOSURE) {
            return decision(classification.taskType(), RiskLevel.HIGH, ExecutionMode.HUMAN_REVIEW,
                    "SENIOR_LEGAL_REVIEW", null, reasons, true);
        }
        if (strongest == RiskSignal.RIGHTS_WAIVER || strongest == RiskSignal.HIGH_VALUE_DISPUTE) {
            return decision(classification.taskType(), RiskLevel.HIGH, ExecutionMode.HUMAN_REVIEW,
                    "SENIOR_LEGAL_REVIEW", null, reasons, true);
        }
        if (strongest == RiskSignal.MISSING_CORE_MATERIAL) {
            return decision(classification.taskType(), RiskLevel.MEDIUM, ExecutionMode.HUMAN_REVIEW,
                    "DOCUMENT_REVIEW", null, reasons, true);
        }
        if (classification.taskType() == TaskType.DISPUTE_ANALYSIS
                && signals.contains(RiskSignal.MULTI_ISSUE_ANALYSIS)) {
            return decision(classification.taskType(), RiskLevel.MEDIUM, ExecutionMode.MULTI_AGENT_WORKFLOW,
                    "DISPUTE_ANALYSIS", "LABOR_DISPUTE_REVIEW", reasons, false);
        }
        return decision(classification.taskType(), RiskLevel.LOW, ExecutionMode.SINGLE_AGENT,
                stableRole(classification.taskType()), null, reasons, false);
    }

    private static RiskSignal strongest(Set<RiskSignal> signals) {
        RiskSignal[] precedence = {RiskSignal.EXTERNAL_ACTION_REQUEST, RiskSignal.IMMINENT_DEADLINE,
                RiskSignal.CRIMINAL_EXPOSURE, RiskSignal.RIGHTS_WAIVER, RiskSignal.HIGH_VALUE_DISPUTE,
                RiskSignal.MISSING_CORE_MATERIAL, RiskSignal.MULTI_ISSUE_ANALYSIS};
        for (RiskSignal signal : precedence) if (signals.contains(signal)) return signal;
        return null;
    }

    private static int precedence(RiskSignal signal) {
        RiskSignal[] order = {RiskSignal.EXTERNAL_ACTION_REQUEST, RiskSignal.IMMINENT_DEADLINE,
                RiskSignal.CRIMINAL_EXPOSURE, RiskSignal.RIGHTS_WAIVER, RiskSignal.HIGH_VALUE_DISPUTE,
                RiskSignal.MISSING_CORE_MATERIAL, RiskSignal.MULTI_ISSUE_ANALYSIS, RiskSignal.CONFLICTING_FACTS};
        for (int i = 0; i < order.length; i++) if (order[i] == signal) return i;
        return order.length;
    }

    private static String stableRole(TaskType type) {
        return switch (type) {
            case CONTRACT_REVIEW -> "CONTRACT_REVIEW";
            case STATUTE_LOOKUP -> "STATUTE_RESEARCH";
            case CASE_RESEARCH -> "CASE_RESEARCH";
            case DISPUTE_ANALYSIS -> "DISPUTE_ANALYSIS";
            case GENERAL_CONSULTATION -> "GENERAL_LEGAL";
        };
    }

    private static RouteDecision decision(TaskType type, RiskLevel level, ExecutionMode mode, String role,
                                          String workflow, List<String> reasons, boolean approval) {
        return new RouteDecision(type, level, mode, role, workflow, reasons, approval, POLICY_VERSION);
    }
}
