package com.raglaw.agentadmin.model;

import com.raglaw.agentadmin.domain.AgentPublishStatus;
import java.util.List;

public record AgentVersionSnapshot(
        String agentCode,
        int version,
        AgentPublishStatus status,
        String model,
        String systemPrompt,
        AgentCapabilityManifest manifest,
        AgentToolPolicy toolPolicy,
        List<String> skills,
        List<String> knowledgeScopes,
        List<String> mcpServers,
        double evaluationScore,
        String configChecksum
) {
    public AgentVersionSnapshot {
        if (agentCode == null || agentCode.isBlank()) throw new IllegalArgumentException("agentCode");
        if (version <= 0) throw new IllegalArgumentException("version");
        if (status == null) throw new IllegalArgumentException("status");
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model");
        if (systemPrompt == null || systemPrompt.isBlank()) throw new IllegalArgumentException("systemPrompt");
        if (manifest == null) throw new IllegalArgumentException("manifest");
        if (toolPolicy == null) throw new IllegalArgumentException("toolPolicy");
        if (evaluationScore < 0 || evaluationScore > 1) throw new IllegalArgumentException("evaluationScore");
        skills = skills == null ? List.of() : List.copyOf(skills);
        knowledgeScopes = knowledgeScopes == null ? List.of() : List.copyOf(knowledgeScopes);
        mcpServers = mcpServers == null ? List.of() : List.copyOf(mcpServers);
        configChecksum = configChecksum == null ? "" : configChecksum;
    }
}
