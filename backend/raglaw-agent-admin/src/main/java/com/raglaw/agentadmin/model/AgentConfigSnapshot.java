package com.raglaw.agentadmin.model;

import java.util.List;

public record AgentConfigSnapshot(
        String code,
        String name,
        String type,
        String model,
        String systemPrompt,
        List<String> skills,
        List<String> knowledgeScopes,
        List<String> a2aPeers,
        List<String> tools
) {
}
