package com.raglaw.agentadmin.dto;

import java.time.Instant;
import java.util.List;

public record AgentConfigDto(
        String id,
        String code,
        String name,
        String type,
        boolean enabled,
        String model,
        List<String> skills,
        List<String> mcpServers,
        List<String> knowledgeScopes,
        List<String> a2aPeers,
        String systemPrompt,
        List<String> tools,
        Instant createdAt,
        Instant updatedAt
) {
}
