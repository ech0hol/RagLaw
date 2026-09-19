package com.raglaw.agentscope.routing;

public interface RiskPolicyEngine {
    RouteDecision decide(RoutingRequest request, RulePrecheckResult precheck, TaskClassification classification);
}
