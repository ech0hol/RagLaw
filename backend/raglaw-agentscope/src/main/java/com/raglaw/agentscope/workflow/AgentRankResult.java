package com.raglaw.agentscope.workflow;

public record AgentRankResult(String agentCode, double confidence, String reason) {
    public AgentRankResult {
        if (confidence < 0 || confidence > 1) throw new IllegalArgumentException("confidence");
        reason = reason == null ? "" : reason;
    }
}
