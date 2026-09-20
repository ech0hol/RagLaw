package com.raglaw.agentadmin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentadmin.domain.AgentPublishStatus;
import com.raglaw.agentadmin.domain.AgentVersionEntity;
import com.raglaw.agentadmin.domain.AgentVersionRepository;
import com.raglaw.agentadmin.dto.AgentVersionDto;
import com.raglaw.agentadmin.dto.PublishAgentRequest;
import com.raglaw.agentadmin.model.AgentCapabilityManifest;
import com.raglaw.agentadmin.model.AgentToolPolicy;
import com.raglaw.agentadmin.model.AgentVersionSnapshot;
import com.raglaw.agentadmin.registry.AgentVersionRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentPublicationService {
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};
    private final AgentVersionRepository repository;
    private final AgentManifestValidator validator;
    private final AgentVersionRegistry registry;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AgentPublicationService(AgentVersionRepository repository, AgentManifestValidator validator,
                                   AgentVersionRegistry registry, ObjectMapper objectMapper) {
        this(repository, validator, registry, objectMapper, Clock.systemUTC());
    }

    AgentPublicationService(AgentVersionRepository repository, AgentManifestValidator validator,
                            AgentVersionRegistry registry, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository; this.validator = validator; this.registry = registry;
        this.objectMapper = objectMapper; this.clock = clock;
    }

    @Transactional
    public AgentVersionDto publish(String agentCode, PublishAgentRequest request, String publisherId) {
        if (request == null) throw new IllegalArgumentException("request");
        AgentVersionEntity version = repository.findByAgentCodeAndVersion(agentCode, request.version())
                .orElseThrow(() -> new IllegalArgumentException("Agent version not found"));
        if (version.getStatus() != AgentPublishStatus.SHADOW && version.getStatus() != AgentPublishStatus.VALIDATING) {
            throw new IllegalStateException("Only validating or shadow versions can be published");
        }
        if (version.getEvaluationScore() < 0.8) throw new IllegalStateException("Evaluation gate not passed");
        AgentVersionSnapshot snapshot = toSnapshot(version);
        AgentManifestValidation validation = validator.validate(snapshot);
        if (!validation.valid()) throw new IllegalStateException("Manifest validation failed: " + validation.errors());
        repository.findTopByAgentCodeAndStatusOrderByVersionDesc(agentCode, AgentPublishStatus.PUBLISHED)
                .ifPresent(previous -> { previous.transitionTo(AgentPublishStatus.DEPRECATED); repository.save(previous); });
        version.publish(publisherId, Instant.now(clock));
        repository.save(version);
        reload();
        return toDto(version);
    }

    public AgentVersionDto publishReadyVersion(String agentCode, int version, String publisherId) {
        return publish(agentCode, new PublishAgentRequest(version, "manual"), publisherId);
    }

    @Transactional(readOnly = true)
    public List<AgentVersionSnapshot> publishedCandidates() {
        return registry.publishedCandidates();
    }

    @Transactional(readOnly = true)
    public void reload() {
        registry.reload(repository.findByStatusOrderByAgentCodeAscVersionDesc(AgentPublishStatus.PUBLISHED).stream()
                .map(this::toSnapshot).toList());
    }

    private AgentVersionSnapshot toSnapshot(AgentVersionEntity entity) {
        try {
            return new AgentVersionSnapshot(entity.getAgentCode(), entity.getVersion(), entity.getStatus(), entity.getModel(),
                    entity.getSystemPrompt(), objectMapper.readValue(entity.getManifestJson(), AgentCapabilityManifest.class),
                    objectMapper.readValue(entity.getToolPolicyJson(), AgentToolPolicy.class),
                    objectMapper.readValue(entity.getSkillsJson(), STRINGS), objectMapper.readValue(entity.getKnowledgeScopesJson(), STRINGS),
                    objectMapper.readValue(entity.getMcpServersJson(), STRINGS), entity.getEvaluationScore(), entity.getConfigChecksum());
        } catch (JsonProcessingException e) { throw new IllegalStateException("Invalid agent version JSON", e); }
    }

    private static AgentVersionDto toDto(AgentVersionEntity entity) {
        return new AgentVersionDto(entity.getAgentCode(), entity.getVersion(), entity.getStatus(), entity.getEvaluationScore(),
                entity.getConfigChecksum(), entity.getCreatedAt(), entity.getPublishedAt(), entity.getPublishedBy());
    }
}
