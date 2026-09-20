package com.raglaw.agentadmin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Entity
@Table(name = "raglaw_agent_version")
public class AgentVersionEntity {
    @Id private String id;
    @Version private long lockVersion;
    @Column(name = "agent_code", nullable = false, length = 64) private String agentCode;
    @Column(nullable = false) private int version;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32) private AgentPublishStatus status;
    @Column(nullable = false, length = 128) private String model;
    @Column(name = "system_prompt", nullable = false, columnDefinition = "MEDIUMTEXT") private String systemPrompt;
    @Column(name = "manifest_json", nullable = false, columnDefinition = "JSON") private String manifestJson;
    @Column(name = "tool_policy_json", nullable = false, columnDefinition = "JSON") private String toolPolicyJson;
    @Column(name = "skills_json", nullable = false, columnDefinition = "JSON") private String skillsJson;
    @Column(name = "knowledge_scopes_json", nullable = false, columnDefinition = "JSON") private String knowledgeScopesJson;
    @Column(name = "mcp_servers_json", nullable = false, columnDefinition = "JSON") private String mcpServersJson;
    @Column(name = "evaluation_score", nullable = false, precision = 6, scale = 5)
    private BigDecimal evaluationScore;
    @Column(name = "config_checksum", nullable = false, length = 128) private String configChecksum;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "created_by", nullable = false, length = 128) private String createdBy;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "published_by", length = 128) private String publishedBy;
    @Column(name = "transitioned_at") private Instant transitionedAt;
    @Column(name = "transitioned_by", length = 128) private String transitionedBy;

    protected AgentVersionEntity() {}

    public AgentVersionEntity(String id, String agentCode, int version, AgentPublishStatus status, String model,
                              String systemPrompt, String manifestJson, String toolPolicyJson, String skillsJson,
                              String knowledgeScopesJson, String mcpServersJson, double evaluationScore,
                              String configChecksum, Instant createdAt) {
        this(id, agentCode, version, status, model, systemPrompt, manifestJson, toolPolicyJson, skillsJson,
                knowledgeScopesJson, mcpServersJson, evaluationScore, configChecksum, createdAt, "system");
    }

    public AgentVersionEntity(String id, String agentCode, int version, AgentPublishStatus status, String model,
                              String systemPrompt, String manifestJson, String toolPolicyJson, String skillsJson,
                              String knowledgeScopesJson, String mcpServersJson, double evaluationScore,
                              String configChecksum, Instant createdAt, String createdBy) {
        this.id = id; this.agentCode = agentCode; this.version = version; this.status = status;
        this.model = model; this.systemPrompt = systemPrompt; this.manifestJson = manifestJson;
        this.toolPolicyJson = toolPolicyJson; this.skillsJson = skillsJson; this.knowledgeScopesJson = knowledgeScopesJson;
        this.mcpServersJson = mcpServersJson; this.evaluationScore = BigDecimal.valueOf(evaluationScore); this.configChecksum = configChecksum;
        this.createdAt = createdAt; this.createdBy = createdBy == null || createdBy.isBlank() ? "system" : createdBy;
    }
    public String getId() { return id; }
    public String getAgentCode() { return agentCode; }
    public int getVersion() { return version; }
    public AgentPublishStatus getStatus() { return status; }
    public String getModel() { return model; }
    public String getSystemPrompt() { return systemPrompt; }
    public String getManifestJson() { return manifestJson; }
    public String getToolPolicyJson() { return toolPolicyJson; }
    public String getSkillsJson() { return skillsJson; }
    public String getKnowledgeScopesJson() { return knowledgeScopesJson; }
    public String getMcpServersJson() { return mcpServersJson; }
    public double getEvaluationScore() { return evaluationScore.doubleValue(); }
    public String getConfigChecksum() { return configChecksum; }
    public Instant getCreatedAt() { return createdAt; }
    public String getCreatedBy() { return createdBy; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getPublishedBy() { return publishedBy; }
    public Instant getTransitionedAt() { return transitionedAt; }
    public String getTransitionedBy() { return transitionedBy; }
    private static final Map<AgentPublishStatus, Set<AgentPublishStatus>> ALLOWED_TRANSITIONS = transitions();

    public void transitionTo(AgentPublishStatus next) {
        transitionTo(next, "system", Instant.now());
    }

    public void transitionTo(AgentPublishStatus next, String actor, Instant now) {
        if (next == null) throw new IllegalArgumentException("next status");
        if (status == next) return;
        if (!ALLOWED_TRANSITIONS.getOrDefault(status, Set.of()).contains(next)) {
            throw new IllegalStateException("Invalid agent version transition: " + status + " -> " + next);
        }
        this.status = next;
        this.transitionedBy = actor == null || actor.isBlank() ? "system" : actor;
        this.transitionedAt = now == null ? Instant.now() : now;
    }

    public void publish(String publisherId, Instant now) {
        transitionTo(AgentPublishStatus.PUBLISHED, publisherId, now);
        this.publishedBy = publisherId;
        this.publishedAt = now;
    }

    private static Map<AgentPublishStatus, Set<AgentPublishStatus>> transitions() {
        Map<AgentPublishStatus, Set<AgentPublishStatus>> result = new EnumMap<>(AgentPublishStatus.class);
        result.put(AgentPublishStatus.DRAFT, EnumSet.of(AgentPublishStatus.VALIDATING, AgentPublishStatus.ARCHIVED));
        result.put(AgentPublishStatus.VALIDATING, EnumSet.of(AgentPublishStatus.SHADOW, AgentPublishStatus.DRAFT, AgentPublishStatus.ARCHIVED));
        result.put(AgentPublishStatus.SHADOW, EnumSet.of(AgentPublishStatus.PUBLISHED, AgentPublishStatus.VALIDATING, AgentPublishStatus.DRAFT, AgentPublishStatus.ARCHIVED));
        result.put(AgentPublishStatus.PUBLISHED, EnumSet.of(AgentPublishStatus.DEPRECATED, AgentPublishStatus.DISABLED));
        result.put(AgentPublishStatus.DEPRECATED, EnumSet.of(AgentPublishStatus.DISABLED, AgentPublishStatus.ARCHIVED));
        result.put(AgentPublishStatus.DISABLED, EnumSet.of(AgentPublishStatus.ARCHIVED));
        result.put(AgentPublishStatus.ARCHIVED, Set.of());
        return result;
    }
}
