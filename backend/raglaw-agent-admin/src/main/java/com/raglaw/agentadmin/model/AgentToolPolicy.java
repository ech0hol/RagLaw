package com.raglaw.agentadmin.model;

import java.util.List;
import java.util.Set;

public record AgentToolPolicy(List<AgentToolGrant> grants, Set<String> mcpServers) {
    public AgentToolPolicy {
        grants = grants == null ? List.of() : List.copyOf(grants);
        mcpServers = mcpServers == null ? Set.of() : Set.copyOf(mcpServers);
    }

    public Set<String> toolNames() {
        return grants.stream().map(AgentToolGrant::toolName).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
