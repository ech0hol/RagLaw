package com.raglaw.agentadmin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "raglaw_agent_version")
public class AgentVersionEntity {
    @Id private String id;
    @Column(name = "agent_code", nullable = false, length = 64) private String agentCode;
    @Column(nullable = false) private int version;
    @Column(nullable = false, length = 32) private AgentPublishStatus status;
    @Column(nullable = false, length = 128) private String model;
    @Column(name = "system_prompt", nullable = false, columnDefinition = "MEDIUMTEXT") private String systemPrompt;
    @Column(name = "manifest_json", nullable = false, columnDefinition = "JSON") private String manifestJson;
    @Column(name = "tool_policy_json", nullable = false, columnDefinition = "JSON") private String toolPolicyJson;
    @Column(name = "skills_json", nullable = false, columnDefinition = "JSON") private String skillsJson;
    @Column(name = "knowledge_scopes_json", nullable = false, columnDefinition = "JSON") private String knowledgeScopesJson;
    @Column(name = "mcp_servers_json", nullable = false, columnDefinition = "JSON") private String mcpServersJson;
    @Column(name = "evaluation_score", nullable = false) private double evaluationScore;
    @Column(name = "config_checksum", nullable = false, length = 128) private String configChecksum;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "published_by", length = 128) private String publishedBy;

    protected AgentVersionEntity() {}

    public AgentVersionEntity(String id, String agentCode, int version, AgentPublishStatus status, String model,
                              String systemPrompt, String manifestJson, String toolPolicyJson, String skillsJson,
                              String knowledgeScopesJson, String mcpServersJson, double evaluationScore,
                              String configChecksum, Instant createdAt) {
        this.id = id; this.agentCode = agentCode; this.version = version; this.status = status;
        this.model = model; this.systemPrompt = systemPrompt; this.manifestJson = manifestJson;
        this.toolPolicyJson = toolPolicyJson; this.skillsJson = skillsJson; this.knowledgeScopesJson = knowledgeScopesJson;
        this.mcpServersJson = mcpServersJson; this.evaluationScore = evaluationScore; this.configChecksum = configChecksum;
        this.createdAt = createdAt;
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
    public double getEvaluationScore() { return evaluationScore; }
    public String getConfigChecksum() { return configChecksum; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getPublishedBy() { return publishedBy; }
    public void transitionTo(AgentPublishStatus status) { this.status = status; }
    public void publish(String publisherId, Instant now) { this.status = AgentPublishStatus.PUBLISHED; this.publishedBy = publisherId; this.publishedAt = now; }
}
