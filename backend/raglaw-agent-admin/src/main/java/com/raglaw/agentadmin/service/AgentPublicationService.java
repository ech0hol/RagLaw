package com.raglaw.agentadmin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentadmin.domain.AgentPublishStatus;
import com.raglaw.agentadmin.domain.AgentVersionEntity;
import com.raglaw.agentadmin.domain.AgentVersionRepository;
import com.raglaw.agentadmin.dto.AgentVersionDto;
import com.raglaw.agentadmin.dto.AgentEvaluationSummaryDto;
import com.raglaw.agentadmin.dto.AgentValidationDto;
import com.raglaw.agentadmin.dto.CreateAgentVersionRequest;
import com.raglaw.agentadmin.dto.PublishAgentRequest;
import com.raglaw.agentadmin.model.AgentCapabilityManifest;
import com.raglaw.agentadmin.model.AgentToolPolicy;
import com.raglaw.agentadmin.model.AgentVersionSnapshot;
import com.raglaw.agentadmin.registry.AgentVersionRegistry;
import java.time.Clock;
import java.time.Instant;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AgentPublicationService {
    // Lifecycle transitions are intentionally persisted before registry reload.
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};
    private final AgentVersionRepository repository;
    private final AgentManifestValidator validator;
    private final AgentVersionRegistry registry;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public AgentPublicationService(AgentVersionRepository repository, AgentManifestValidator validator,
                                   AgentVersionRegistry registry, ObjectMapper objectMapper) {
        this(repository, validator, registry, objectMapper, Clock.systemUTC());
    }

    AgentPublicationService(AgentVersionRepository repository, AgentManifestValidator validator,
                            AgentVersionRegistry registry, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository; this.validator = validator; this.registry = registry;
        this.objectMapper = objectMapper; this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        reload();
    }

    @Transactional
    public AgentVersionDto createVersion(String agentCode, CreateAgentVersionRequest request, String actor) {
        if (request == null) throw new IllegalArgumentException("request");
        if (agentCode == null || agentCode.isBlank()) throw new IllegalArgumentException("agentCode");
        String normalizedAgentCode = agentCode.trim();
        if (request.manifest() == null || request.toolPolicy() == null) {
            throw new IllegalArgumentException("manifest and toolPolicy are required");
        }
        int version = request.version() == null
                ? repository.findTopByAgentCodeOrderByVersionDesc(normalizedAgentCode).map(v -> v.getVersion() + 1).orElse(1)
                : request.version();
        if (version <= 0) throw new IllegalArgumentException("version");
        if (repository.findByAgentCodeAndVersion(normalizedAgentCode, version).isPresent()) {
            throw new IllegalArgumentException("Agent version already exists");
        }
        double evaluationScore = request.evaluationScore() == null ? 0.0 : request.evaluationScore();
        if (evaluationScore < 0 || evaluationScore > 1) throw new IllegalArgumentException("evaluationScore");
        String checksum = checksum(request);
        AgentVersionEntity entity = new AgentVersionEntity(UUID.randomUUID().toString(), normalizedAgentCode, version,
                AgentPublishStatus.DRAFT, requireText(request.model(), "model"), requireText(request.systemPrompt(), "systemPrompt"),
                writeJson(request.manifest()), writeJson(request.toolPolicy()), writeJson(request.skills()),
                writeJson(request.knowledgeScopes()), writeJson(request.mcpServers()),
                evaluationScore, checksum, Instant.now(clock), actor);
        repository.save(entity);
        return toDto(entity);
    }

    @Transactional
    public AgentValidationDto validate(String agentCode, int version) {
        AgentVersionEntity entity = require(agentCode, version);
        AgentManifestValidation validation = validator.validate(toSnapshot(entity));
        if (validation.valid() && entity.getStatus() == AgentPublishStatus.DRAFT) {
            entity.transitionTo(AgentPublishStatus.VALIDATING);
            repository.save(entity);
        }
        return new AgentValidationDto(agentCode, version, validation.valid(), validation.errors(), entity.getStatus());
    }

    @Transactional
    public AgentVersionDto enterShadow(String agentCode, int version) {
        AgentVersionEntity entity = require(agentCode, version);
        if (entity.getStatus() != AgentPublishStatus.VALIDATING) {
            throw new IllegalStateException("Only validating versions can enter shadow");
        }
        entity.transitionTo(AgentPublishStatus.SHADOW);
        repository.save(entity);
        return toDto(entity);
    }

    @Transactional
    public AgentVersionDto deprecate(String agentCode, int version) {
        return deprecate(agentCode, version, "system");
    }

    @Transactional
    public AgentVersionDto deprecate(String agentCode, int version, String actor) {
        return transitionTo(agentCode, version, AgentPublishStatus.DEPRECATED, actor);
    }

    @Transactional
    public AgentVersionDto disable(String agentCode, int version) {
        return disable(agentCode, version, "system");
    }

    @Transactional
    public AgentVersionDto disable(String agentCode, int version, String actor) {
        AgentVersionDto dto = transition(agentCode, version, AgentPublishStatus.DISABLED, actor);
        reloadAfterCommit();
        return dto;
    }

    @Transactional
    public AgentVersionDto archive(String agentCode, int version) {
        return archive(agentCode, version, "system");
    }

    @Transactional
    public AgentVersionDto archive(String agentCode, int version, String actor) {
        AgentVersionDto dto = transition(agentCode, version, AgentPublishStatus.ARCHIVED, actor);
        reloadAfterCommit();
        return dto;
    }

    @Transactional
    public AgentVersionDto transitionTo(String agentCode, int version, AgentPublishStatus next) {
        return transitionTo(agentCode, version, next, "system");
    }

    @Transactional
    public AgentVersionDto transitionTo(String agentCode, int version, AgentPublishStatus next, String actor) {
        if (next == null) throw new IllegalArgumentException("targetStatus");
        if (next == AgentPublishStatus.PUBLISHED) {
            throw new IllegalStateException("Use the publish operation to enter PUBLISHED");
        }
        AgentVersionDto dto = transition(agentCode, version, next, actor);
        reloadAfterCommit();
        return dto;
    }

    @Transactional(readOnly = true)
    public List<AgentVersionDto> history(String agentCode) {
        return repository.findByAgentCodeOrderByVersionDesc(agentCode).stream().map(AgentPublicationService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public AgentEvaluationSummaryDto evaluationSummary(String agentCode, int version) {
        AgentVersionEntity entity = require(agentCode, version);
        return new AgentEvaluationSummaryDto(entity.getAgentCode(), entity.getVersion(), entity.getEvaluationScore(),
                entity.getConfigChecksum(), entity.getStatus().name());
    }

    @Transactional
    public AgentVersionDto publish(String agentCode, PublishAgentRequest request, String publisherId) {
        if (request == null) throw new IllegalArgumentException("request");
        AgentVersionEntity version = repository.findByAgentCodeAndVersion(agentCode, request.version())
                .orElseThrow(() -> new IllegalArgumentException("Agent version not found"));
        if (version.getStatus() != AgentPublishStatus.SHADOW) {
            throw new IllegalStateException("Only shadow versions can be published");
        }
        if (version.getEvaluationScore() < 0.8) throw new IllegalStateException("Evaluation gate not passed");
        AgentVersionSnapshot snapshot = toSnapshot(version);
        AgentManifestValidation validation = validator.validate(snapshot);
        if (!validation.valid()) throw new IllegalStateException("Manifest validation failed: " + validation.errors());
        repository.findTopByAgentCodeAndStatusOrderByVersionDesc(agentCode, AgentPublishStatus.PUBLISHED)
                .ifPresent(previous -> { previous.transitionTo(AgentPublishStatus.DEPRECATED); repository.save(previous); });
        version.publish(publisherId, Instant.now(clock));
        repository.save(version);
        reloadAfterCommit();
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
        registry.reload(repository.findAllByOrderByAgentCodeAscVersionDesc().stream()
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

    private AgentVersionDto transition(String agentCode, int version, AgentPublishStatus next, String actor) {
        AgentVersionEntity entity = require(agentCode, version);
        entity.transitionTo(next, actor, Instant.now(clock));
        repository.save(entity);
        return toDto(entity);
    }

    private void reloadAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            reload();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { reload(); }
        });
    }

    private AgentVersionEntity require(String agentCode, int version) {
        return repository.findByAgentCodeAndVersion(agentCode, version)
                .orElseThrow(() -> new IllegalArgumentException("Agent version not found"));
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value == null ? List.of() : value); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Invalid agent version JSON", e); }
    }

    private String checksum(CreateAgentVersionRequest request) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(writeJson(request).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name);
        return value.trim();
    }

    private static AgentVersionDto toDto(AgentVersionEntity entity) {
        return new AgentVersionDto(entity.getAgentCode(), entity.getVersion(), entity.getStatus(), entity.getEvaluationScore(),
                entity.getConfigChecksum(), entity.getCreatedAt(), entity.getCreatedBy(), entity.getPublishedAt(), entity.getPublishedBy(),
                entity.getTransitionedAt(), entity.getTransitionedBy());
    }
}
