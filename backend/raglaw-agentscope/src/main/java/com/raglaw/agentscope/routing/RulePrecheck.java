package com.raglaw.agentscope.routing;

public interface RulePrecheck {
    RulePrecheckResult evaluate(RoutingRequest request);
}
