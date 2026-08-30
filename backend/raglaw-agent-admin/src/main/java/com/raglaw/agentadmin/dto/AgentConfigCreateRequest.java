package com.raglaw.agentadmin.dto;

import java.util.List;

public record AgentConfigCreateRequest(
        String code,
        String name,
        String model,
        String systemPrompt,
        List<String> skills,
        List<String> tools,
        List<String> mcpServers,
        List<String> knowledgeScopes,
        Boolean enabled
) {
}
