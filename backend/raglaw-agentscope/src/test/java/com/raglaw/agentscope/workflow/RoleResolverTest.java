package com.raglaw.agentscope.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.raglaw.agentadmin.domain.AgentPublishStatus;
import com.raglaw.agentadmin.model.AgentCapabilityManifest;
import com.raglaw.agentadmin.model.AgentToolGrant;
import com.raglaw.agentadmin.model.AgentToolPolicy;
import com.raglaw.agentadmin.model.AgentVersionSnapshot;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoleResolverTest {
    @Test
    void filteredCandidateCannotBeRestoredByScore() {
        var requirement = new RoleRequirement("LEGAL_ANALYSIS", Set.of("LEGAL_ANALYSIS"), Set.of("LABOR_LAW"), Set.of("rag_search"), Set.of("CASE_FACTS"), "HIGH", 0.8, "labor_expert");
        var context = new AgentResolutionContext("tenant", "LEGAL_ANALYSIS", "HIGH", Set.of("CASE_FACTS"), Set.of("rag_search"), Set.of("rag_search"), List.of(
                snapshot("general_expert", 9, AgentPublishStatus.PUBLISHED, 0.99, Set.of("CONTRACT_LAW")),
                snapshot("labor_expert", 3, AgentPublishStatus.PUBLISHED, 0.85, Set.of("LABOR_LAW")),
                snapshot("draft_expert", 100, AgentPublishStatus.DRAFT, 1.0, Set.of("LABOR_LAW"))));

        ResolvedAgent result = new RoleResolver().resolve(requirement, context);
        assertThat(result.agentCode()).isEqualTo("labor_expert");
        assertThat(result.agentVersion()).isEqualTo(3);
    }

    @Test
    void noEligibleCandidateReturnsExplicitFailure() {
        var requirement = new RoleRequirement("WRITE", Set.of("LEGAL_ANALYSIS"), Set.of("LABOR_LAW"), Set.of("tavily-search"), Set.of(), "HIGH", 0.0, "");
        var context = new AgentResolutionContext("tenant", "LEGAL_ANALYSIS", "HIGH", Set.of(), Set.of("rag_search"), Set.of("rag_search"), List.of());
        assertThatThrownBy(() -> new RoleResolver().resolve(requirement, context)).isInstanceOf(NoEligibleAgentException.class);
    }

    private static AgentVersionSnapshot snapshot(String code, int version, AgentPublishStatus status, double score, Set<String> domains) {
        return new AgentVersionSnapshot(code, version, status, "dashscope:qwen-plus", "prompt",
                new AgentCapabilityManifest(domains, Set.of("LEGAL_ANALYSIS"), Set.of("LEGAL_ANALYSIS"), Set.of("HIGH"), Set.of("CASE_FACTS"), "Result"),
                new AgentToolPolicy(List.of(new AgentToolGrant("rag_search", "READ_ONLY", false, Set.of(), 1000)), Set.of()),
                List.of(), List.of(), List.of(), score, code + "-sha");
    }
}
