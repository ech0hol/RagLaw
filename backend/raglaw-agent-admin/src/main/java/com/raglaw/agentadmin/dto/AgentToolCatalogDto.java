package com.raglaw.agentadmin.dto;

import java.util.List;

public record AgentToolCatalogDto(
        List<CatalogTool> builtinTools,
        List<CatalogMcpServer> mcpServers,
        List<CatalogSkill> builtinSkills,
        boolean globalMcpEnabled
) {
    public record CatalogTool(String id, String label, String description) {
    }

    public record CatalogMcpServer(
            String id,
            String label,
            List<String> tools,
            String requiresEnv
    ) {
    }

    public record CatalogSkill(String id, String label, String description) {
    }
}
