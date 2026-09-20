package com.raglaw.agentscope.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentscope.domain.AgentResolutionObservationEntity;
import com.raglaw.agentscope.domain.AgentResolutionObservationRepository;
import com.raglaw.agentadmin.model.AgentVersionSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ShadowAgentCandidateRanker {
    private final RoleResolver resolver;
    private final AgentResolutionObservationRepository repository;
    private final ObjectMapper objectMapper;
    private final Optional<AgentCandidateRanker> modelRanker;

    @Autowired
    public ShadowAgentCandidateRanker(RoleResolver resolver, AgentResolutionObservationRepository repository,
                                      ObjectMapper objectMapper, Optional<AgentCandidateRanker> modelRanker) {
        this.resolver = resolver; this.repository = repository; this.objectMapper = objectMapper; this.modelRanker = modelRanker;
    }

    ShadowAgentCandidateRanker(RoleResolver resolver, AgentResolutionObservationRepository repository,
                               ObjectMapper objectMapper, AgentCandidateRanker modelRanker) {
        this.resolver = resolver; this.repository = repository; this.objectMapper = objectMapper; this.modelRanker = Optional.ofNullable(modelRanker);
    }

    public ResolvedAgent resolveAndObserve(RoleRequirement requirement, AgentResolutionContext context, String traceId) {
        ResolvedAgent deterministic = resolver.resolve(requirement, context);
        List<AgentVersionSnapshot> eligible = resolver.eligibleCandidates(requirement, context);
        AgentRankResult model = null;
        if (modelRanker.isPresent()) {
            try { model = modelRanker.get().rank(requirement, eligible); }
            catch (RuntimeException ignored) { /* shadow ranking can never fail the authoritative path */ }
        }
        boolean agreement = model != null && deterministic.agentCode().equals(model.agentCode());
        try {
            String candidates = objectMapper.writeValueAsString(eligible.stream().map(c -> c.agentCode() + ":" + c.version()).toList());
            repository.save(new AgentResolutionObservationEntity(UUID.randomUUID().toString(), traceId, requirement.roleCode(),
                    deterministic.agentCode(), model == null ? null : model.agentCode(), agreement,
                    model == null ? 0 : model.confidence(), candidates, Instant.now()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize resolution observation", exception);
        }
        return deterministic;
    }
}
