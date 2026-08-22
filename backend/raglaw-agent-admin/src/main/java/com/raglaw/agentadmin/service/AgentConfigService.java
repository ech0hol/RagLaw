package com.raglaw.agentadmin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentadmin.domain.AgentConfigEntity;
import com.raglaw.agentadmin.domain.AgentConfigRepository;
import com.raglaw.agentadmin.dto.AgentConfigDto;
import com.raglaw.agentadmin.dto.AgentConfigUpdateRequest;
import com.raglaw.agentadmin.model.AgentConfigSnapshot;
import com.raglaw.agentadmin.registry.AgentRegistry;
import java.util.List;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentConfigService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final AgentConfigRepository repository;
    private final AgentRegistry registry;
    private final ObjectMapper objectMapper;

    public AgentConfigService(
            AgentConfigRepository repository,
            AgentRegistry registry,
            ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        reload();
    }

    public List<AgentConfigDto> list() {
        return repository.findAllByOrderByCodeAsc().stream()
                .map(this::toDto)
                .toList();
    }

    public AgentConfigDto get(String code) {
        return repository.findByCode(code)
                .map(this::toDto)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + code));
    }

    @Transactional
    public AgentConfigDto update(String code, AgentConfigUpdateRequest request) {
        AgentConfigEntity entity = repository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + code));

        if (request.name() != null) {
            entity.setName(request.name());
        }
        if (request.type() != null) {
            entity.setType(request.type());
        }
        if (request.enabled() != null) {
            entity.setEnabled(request.enabled());
        }
        if (request.model() != null) {
            entity.setModel(request.model());
        }
        if (request.skills() != null) {
            entity.setSkillsJson(writeJson(request.skills()));
        }
        if (request.mcpServers() != null) {
            entity.setMcpServersJson(writeJson(request.mcpServers()));
        }
        if (request.knowledgeScopes() != null) {
            entity.setKnowledgeScopesJson(writeJson(request.knowledgeScopes()));
        }
        if (request.a2aPeers() != null) {
            entity.setA2aPeersJson(writeJson(request.a2aPeers()));
        }
        if (request.systemPrompt() != null) {
            entity.setSystemPrompt(request.systemPrompt());
        }
        if (request.tools() != null) {
            entity.setToolsJson(writeJson(request.tools()));
        }
        entity.touchUpdatedAt();
        repository.save(entity);
        return toDto(entity);
    }

    public void reload() {
        List<AgentConfigSnapshot> snapshots = repository.findByEnabledTrue().stream()
                .map(this::toSnapshot)
                .toList();
        registry.reload(snapshots);
    }

    private AgentConfigSnapshot toSnapshot(AgentConfigEntity entity) {
        return new AgentConfigSnapshot(
                entity.getCode(),
                entity.getName(),
                entity.getType(),
                entity.getModel(),
                entity.getSystemPrompt(),
                readStringList(entity.getSkillsJson()),
                readStringList(entity.getKnowledgeScopesJson()),
                readStringList(entity.getA2aPeersJson()),
                readStringList(entity.getToolsJson())
        );
    }

    private AgentConfigDto toDto(AgentConfigEntity entity) {
        return new AgentConfigDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getType(),
                entity.isEnabled(),
                entity.getModel(),
                readStringList(entity.getSkillsJson()),
                readStringList(entity.getMcpServersJson()),
                readStringList(entity.getKnowledgeScopesJson()),
                readStringList(entity.getA2aPeersJson()),
                entity.getSystemPrompt(),
                readStringList(entity.getToolsJson()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String writeJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values != null ? values : List.of());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize JSON list", e);
        }
    }
}
