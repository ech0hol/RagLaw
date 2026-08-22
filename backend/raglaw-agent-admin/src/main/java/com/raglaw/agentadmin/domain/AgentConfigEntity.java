package com.raglaw.agentadmin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "raglaw_agent_config")
public class AgentConfigEntity {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private String model = "dashscope:qwen-plus";

    @Column(name = "skills_json")
    private String skillsJson;

    @Column(name = "mcp_servers_json")
    private String mcpServersJson;

    @Column(name = "knowledge_scopes_json")
    private String knowledgeScopesJson;

    @Column(name = "a2a_peers_json")
    private String a2aPeersJson;

    @Column(name = "system_prompt")
    private String systemPrompt;

    @Column(name = "tools_json")
    private String toolsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentConfigEntity() {
    }

    public AgentConfigEntity(
            String id,
            String code,
            String name,
            String type,
            boolean enabled,
            String model,
            String skillsJson,
            String knowledgeScopesJson,
            String a2aPeersJson,
            String systemPrompt,
            String toolsJson
    ) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.type = type;
        this.enabled = enabled;
        this.model = model;
        this.skillsJson = skillsJson;
        this.knowledgeScopesJson = knowledgeScopesJson;
        this.a2aPeersJson = a2aPeersJson;
        this.systemPrompt = systemPrompt;
        this.toolsJson = toolsJson;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getSkillsJson() {
        return skillsJson;
    }

    public void setSkillsJson(String skillsJson) {
        this.skillsJson = skillsJson;
    }

    public String getMcpServersJson() {
        return mcpServersJson;
    }

    public void setMcpServersJson(String mcpServersJson) {
        this.mcpServersJson = mcpServersJson;
    }

    public String getKnowledgeScopesJson() {
        return knowledgeScopesJson;
    }

    public void setKnowledgeScopesJson(String knowledgeScopesJson) {
        this.knowledgeScopesJson = knowledgeScopesJson;
    }

    public String getA2aPeersJson() {
        return a2aPeersJson;
    }

    public void setA2aPeersJson(String a2aPeersJson) {
        this.a2aPeersJson = a2aPeersJson;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getToolsJson() {
        return toolsJson;
    }

    public void setToolsJson(String toolsJson) {
        this.toolsJson = toolsJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void touchUpdatedAt() {
        this.updatedAt = Instant.now();
    }
}
