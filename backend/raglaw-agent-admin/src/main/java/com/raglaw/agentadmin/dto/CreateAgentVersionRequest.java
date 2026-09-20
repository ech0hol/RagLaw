package com.raglaw.agentadmin.dto;

import com.raglaw.agentadmin.model.AgentCapabilityManifest;
import com.raglaw.agentadmin.model.AgentToolPolicy;
import java.util.List;

public record CreateAgentVersionRequest(
        Integer version,
        String model,
        String systemPrompt,
        AgentCapabilityManifest manifest,
        AgentToolPolicy toolPolicy,
        List<String> skills,
        List<String> knowledgeScopes,
        List<String> mcpServers,
        Double evaluationScore,
        String configChecksum
) {}
