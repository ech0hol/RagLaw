package com.raglaw.agentadmin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentadmin.domain.AgentConfigEntity;
import com.raglaw.agentadmin.domain.AgentConfigRepository;
import com.raglaw.agentadmin.dto.AgentConfigCreateRequest;
import com.raglaw.agentadmin.dto.AgentConfigDto;
import com.raglaw.agentadmin.dto.AgentConfigUpdateRequest;
import com.raglaw.agentadmin.dto.AgentToolCatalogDto;
import com.raglaw.agentadmin.model.AgentConfigSnapshot;
import com.raglaw.agentadmin.registry.AgentRegistry;
import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentConfigService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private static final Set<String> ALLOWED_TOOLS = Set.of("rag_search", "tavily-search");
    private static final Set<String> ALLOWED_MCP_SERVERS = Set.of("tavily");
    private static final Set<String> ALLOWED_SKILLS = Set.of("risk-dimension-review");
    private static final Set<String> BUILTIN_AGENT_CODES = Set.of("GENERAL", "STATUTE", "CASE", "CONTRACT");
    private static final Pattern AGENT_CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,31}$");
    private static final String DEFAULT_MODEL = "dashscope:qwen-plus";
    private static final String DEFAULT_SYSTEM_PROMPT = "你是法律助手，请基于工具检索结果作答。";

    private final AgentConfigRepository repository;
    private final AgentRegistry registry;
    private final ObjectMapper objectMapper;
    private final boolean globalMcpEnabled;

    public AgentConfigService(
            AgentConfigRepository repository,
            AgentRegistry registry,
            ObjectMapper objectMapper,
            @Value("${raglaw.agentscope.mcp.enabled:false}") boolean globalMcpEnabled
    ) {
        this.repository = repository;
        this.registry = registry;
        this.objectMapper = objectMapper;
        this.globalMcpEnabled = globalMcpEnabled;
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
            entity.setSkillsJson(writeJson(normalizeSkills(request.skills())));
        }
        if (request.mcpServers() != null) {
            entity.setMcpServersJson(writeJson(normalizeMcpServers(request.mcpServers())));
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
            entity.setToolsJson(writeJson(normalizeTools(request.tools())));
        }
        normalizeToolsAndMcp(entity);
        entity.touchUpdatedAt();
        repository.save(entity);
        reload();
        return toDto(entity);
    }

    @Transactional
    public AgentConfigDto create(AgentConfigCreateRequest request) {
        String code = normalizeCode(request.code());
        validateNewCode(code);

        String name = request.name() == null ? "" : request.name().trim();
        if (name.isBlank()) {
            throw new BusinessException(ErrorCodes.VALIDATION, "Agent 名称不能为空");
        }

        String model = request.model() == null || request.model().isBlank()
                ? DEFAULT_MODEL
                : request.model().trim();
        String systemPrompt = request.systemPrompt() == null || request.systemPrompt().isBlank()
                ? DEFAULT_SYSTEM_PROMPT
                : request.systemPrompt().trim();
        boolean enabled = request.enabled() == null || request.enabled();

        AgentConfigEntity entity = new AgentConfigEntity(
                UUID.randomUUID().toString(),
                code,
                name,
                "GENERAL",
                enabled,
                model,
                writeJson(normalizeSkills(request.skills())),
                writeJson(request.knowledgeScopes()),
                "[]",
                systemPrompt,
                writeJson(request.tools())
        );
        entity.setMcpServersJson(writeJson(request.mcpServers()));
        normalizeToolsAndMcp(entity);
        repository.save(entity);
        reload();
        return toDto(entity);
    }

    @Transactional
    public void delete(String code) {
        String normalized = normalizeCode(code);
        if (BUILTIN_AGENT_CODES.contains(normalized)) {
            throw new BusinessException(ErrorCodes.VALIDATION, "内置 Agent 不可删除");
        }
        AgentConfigEntity entity = repository.findByCode(normalized)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "Agent 不存在"));
        repository.delete(entity);
        reload();
    }

    public AgentToolCatalogDto catalog() {
        return new AgentToolCatalogDto(
                List.of(
                        new AgentToolCatalogDto.CatalogTool(
                                "rag_search",
                                "知识库检索",
                                "检索法规、案例片段（走知识漏斗 + 双通道）"
                        ),
                        new AgentToolCatalogDto.CatalogTool(
                                "tavily-search",
                                "联网搜索",
                                "通过 Tavily 检索最新公开网页信息（需全局 MCP 开启）"
                        )
                ),
                List.of(
                        new AgentToolCatalogDto.CatalogMcpServer(
                                "tavily",
                                "Tavily 联网",
                                List.of("tavily-search"),
                                "TAVILY_API_KEY"
                        )
                ),
                List.of(
                        new AgentToolCatalogDto.CatalogSkill(
                                "risk-dimension-review",
                                "合同风险维度审查",
                                "按风险维度分析合同条款"
                        )
                ),
                globalMcpEnabled
        );
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
                readStringList(entity.getToolsJson()),
                readStringList(entity.getMcpServersJson())
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

    private void validateNewCode(String code) {
        if (!AGENT_CODE_PATTERN.matcher(code).matches()) {
            throw new BusinessException(
                    ErrorCodes.VALIDATION,
                    "Agent 编码须以大写字母开头，仅含大写字母、数字与下划线，长度 2–32"
            );
        }
        if (BUILTIN_AGENT_CODES.contains(code)) {
            throw new BusinessException(ErrorCodes.VALIDATION, "不能使用内置 Agent 编码");
        }
        if (repository.existsByCode(code)) {
            throw new BusinessException(ErrorCodes.VALIDATION, "Agent 编码已存在");
        }
    }

    private static String normalizeCode(String code) {
        if (code == null) {
            throw new BusinessException(ErrorCodes.VALIDATION, "Agent 编码不能为空");
        }
        return code.trim().toUpperCase();
    }

    private List<String> normalizeTools(List<String> tools) {
        if (tools == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String tool : tools) {
            if (tool != null && ALLOWED_TOOLS.contains(tool.trim())) {
                normalized.add(tool.trim());
            }
        }
        return normalized;
    }

    private List<String> normalizeMcpServers(List<String> mcpServers) {
        if (mcpServers == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String server : mcpServers) {
            if (server != null && ALLOWED_MCP_SERVERS.contains(server.trim())) {
                normalized.add(server.trim());
            }
        }
        return normalized;
    }

    private List<String> normalizeSkills(List<String> skills) {
        if (skills == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String skill : skills) {
            if (skill != null && ALLOWED_SKILLS.contains(skill.trim())) {
                normalized.add(skill.trim());
            }
        }
        return normalized;
    }

    private void normalizeToolsAndMcp(AgentConfigEntity entity) {
        List<String> tools = normalizeTools(readStringList(entity.getToolsJson()));
        List<String> mcpServers = new ArrayList<>(normalizeMcpServers(readStringList(entity.getMcpServersJson())));
        if (tools.contains("tavily-search") && !mcpServers.contains("tavily")) {
            mcpServers.add("tavily");
        }
        entity.setToolsJson(writeJson(tools));
        entity.setMcpServersJson(writeJson(mcpServers));
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
