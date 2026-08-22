package com.raglaw.agentadmin.dto;

import java.util.List;

public record AgentConfigUpdateRequest(
        String name,
        String type,
        Boolean enabled,
        String model,
        List<String> skills,
        List<String> mcpServers,
        List<String> knowledgeScopes,
        List<String> a2aPeers,
        String systemPrompt,
        List<String> tools
) {
}
