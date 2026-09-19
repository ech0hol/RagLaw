package com.raglaw.agentscope.routing;

import com.raglaw.agentscope.workflow.WorkflowDefinition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public final class TaskRoutingService {
    private final RulePrecheck precheck;
    private final TaskClassifier classifier;
    private final RiskPolicyEngine policy;
    private final WorkflowCatalog catalog;

    public TaskRoutingService(RulePrecheck precheck, TaskClassifier classifier,
                              RiskPolicyEngine policy, WorkflowCatalog catalog) {
        this.precheck = require(precheck, "precheck");
        this.classifier = require(classifier, "classifier");
        this.policy = require(policy, "policy");
        this.catalog = require(catalog, "catalog");
    }

    public RouteDecision route(RoutingRequest request) {
        if (request == null) throw new IllegalArgumentException("request is required");
        RulePrecheckResult precheckResult = precheck.evaluate(request);
        TaskClassification classification = classifier.classify(request);
        RouteDecision decision = policy.decide(request, precheckResult, classification);
        if (decision.executionMode() != ExecutionMode.MULTI_AGENT_WORKFLOW) return decision;

        WorkflowDefinition workflow = catalog.match(classification).orElse(null);
        if (workflow == null) return clarification(decision, "workflow_clarification: no catalog match for " + classification.taskType());
        List<String> missing = missingMaterials(workflow, classification, request);
        if (!missing.isEmpty()) return clarification(decision, "workflow_clarification: missing required materials " + missing);
        return decision;
    }

    private static List<String> missingMaterials(WorkflowDefinition workflow, TaskClassification classification, RoutingRequest request) {
        List<String> missing = new ArrayList<>();
        for (String material : workflow.requiredMaterials()) {
            boolean classifiedMissing = classification.missingMaterials().stream().anyMatch(value -> value.equalsIgnoreCase(material));
            boolean contractMissing = !request.hasContractDocument() && isContractMaterial(material);
            if (classifiedMissing || contractMissing) missing.add(material);
        }
        return missing;
    }

    private static boolean isContractMaterial(String material) {
        String normalized = material.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("contract") || normalized.contains("合同");
    }

    private static RouteDecision clarification(RouteDecision decision, String reason) {
        List<String> reasons = new ArrayList<>();
        reasons.add(reason);
        reasons.addAll(decision.policyReasons());
        return new RouteDecision(decision.taskType(), decision.riskLevel(), ExecutionMode.HUMAN_REVIEW,
                decision.expertRole(), decision.workflowCode(), reasons, true, decision.policyVersion());
    }

    private static <T> T require(T value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " is required");
        return value;
    }
}
