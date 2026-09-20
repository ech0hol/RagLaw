package com.raglaw.agentscope.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentadmin.domain.AgentPublishStatus;
import com.raglaw.agentadmin.model.AgentCapabilityManifest;
import com.raglaw.agentadmin.model.AgentToolGrant;
import com.raglaw.agentadmin.model.AgentToolPolicy;
import com.raglaw.agentadmin.model.AgentVersionSnapshot;
import com.raglaw.agentscope.domain.AgentResolutionObservationEntity;
import com.raglaw.agentscope.domain.AgentResolutionObservationRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ShadowAgentCandidateRankerTest {
    @Test
    void modelDisagreementDoesNotChangeWinnerInShadowMode() {
        AgentResolutionObservationRepository repository = Mockito.mock(AgentResolutionObservationRepository.class);
        AgentCandidateRanker model = Mockito.mock(AgentCandidateRanker.class);
        when(model.rank(any(), any())).thenReturn(new AgentRankResult("general_expert", 0.91, "semantic tie-break"));
        var service = new ShadowAgentCandidateRanker(new RoleResolver(), repository, new ObjectMapper(), model);
        var requirement = new RoleRequirement("LEGAL_ANALYSIS", Set.of("LEGAL_ANALYSIS"), Set.of("LABOR_LAW"), Set.of("rag_search"), Set.of("CASE_FACTS"), "HIGH", 0.8, "labor_expert");
        var context = new AgentResolutionContext("tenant", "LEGAL_ANALYSIS", "HIGH", Set.of("CASE_FACTS"), Set.of("rag_search"), Set.of("rag_search"), List.of(snapshot("labor_expert", 3, 0.85, "LABOR_LAW"), snapshot("general_expert", 2, 0.90, "LABOR_LAW")));

        ResolvedAgent resolved = service.resolveAndObserve(requirement, context, "trace-1");

        assertThat(resolved.agentCode()).isEqualTo("labor_expert");
        verify(repository).save(any(AgentResolutionObservationEntity.class));
    }

    private static AgentVersionSnapshot snapshot(String code, int version, double score, String domain) {
        return new AgentVersionSnapshot(code, version, AgentPublishStatus.PUBLISHED, "dashscope:qwen-plus", "prompt",
                new AgentCapabilityManifest(Set.of(domain), Set.of("LEGAL_ANALYSIS"), Set.of("LEGAL_ANALYSIS"), Set.of("HIGH"), Set.of("CASE_FACTS"), "Result"),
                new AgentToolPolicy(List.of(new AgentToolGrant("rag_search", "READ_ONLY", false, Set.of(), 1000)), Set.of()), List.of(), List.of(), List.of(), score, code);
    }
}
