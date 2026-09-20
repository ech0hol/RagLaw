package com.raglaw.agentadmin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.raglaw.agentadmin.model.AgentToolGrant;
import com.raglaw.agentadmin.model.AgentToolPolicy;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EffectiveToolPolicyTest {
    @Test
    void effectiveToolsAreAnIntersection() {
        AgentToolPolicy policy = new AgentToolPolicy(List.of(
                new AgentToolGrant("rag_search", "READ_ONLY", false, Set.of("CASE_FACTS"), 1_000),
                new AgentToolGrant("tavily-search", "NETWORK", true, Set.of(), 1_000)), Set.of("tavily"));
        assertThat(new EffectiveToolPolicy().resolve(policy,
                Set.of("rag_search"), Set.of("rag_search", "tavily-search"), Set.of("rag_search")))
                .containsExactly("rag_search");
    }
}
