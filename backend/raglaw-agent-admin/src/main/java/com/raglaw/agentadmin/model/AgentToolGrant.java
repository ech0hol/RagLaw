package com.raglaw.agentadmin.model;

import java.util.Set;

public record AgentToolGrant(
        String toolName,
        String sideEffectLevel,
        boolean approvalRequired,
        Set<String> dataScopes,
        long timeoutMs
) {
    public AgentToolGrant {
        if (toolName == null || toolName.isBlank()) throw new IllegalArgumentException("toolName");
        toolName = toolName.trim();
        if (sideEffectLevel == null || sideEffectLevel.isBlank()) throw new IllegalArgumentException("sideEffectLevel");
        sideEffectLevel = sideEffectLevel.trim().toUpperCase();
        if (!Set.of("READ_ONLY", "NETWORK", "WRITE").contains(sideEffectLevel)) {
            throw new IllegalArgumentException("sideEffectLevel");
        }
        dataScopes = dataScopes == null ? Set.of() : Set.copyOf(dataScopes);
        if (timeoutMs <= 0) throw new IllegalArgumentException("timeoutMs");
    }
}
