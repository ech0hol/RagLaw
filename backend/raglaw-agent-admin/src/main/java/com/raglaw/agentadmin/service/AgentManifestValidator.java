package com.raglaw.agentadmin.service;

import com.raglaw.agentadmin.model.AgentToolGrant;
import com.raglaw.agentadmin.model.AgentVersionSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class AgentManifestValidator {
    private static final Set<String> KNOWN_TOOLS = Set.of("rag_search", "tavily-search", "history_lookup");
    private static final Set<String> KNOWN_MCP = Set.of("tavily");
    private static final Set<String> MCP_TOOLS = Set.of("tavily-search");
    private static final Set<String> RISK_LEVELS = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Pattern MODEL = Pattern.compile("(?:dashscope|openai):[a-z0-9._-]+", Pattern.CASE_INSENSITIVE);

    public AgentManifestValidation validate(AgentVersionSnapshot snapshot) {
        List<String> errors = new ArrayList<>();
        if (snapshot == null) return new AgentManifestValidation(List.of("NULL_SNAPSHOT"), List.of());
        if (snapshot.systemPrompt() == null || snapshot.systemPrompt().isBlank()) errors.add("BLANK_SYSTEM_PROMPT");
        if (snapshot.model() == null || !MODEL.matcher(snapshot.model()).matches()) errors.add("UNSUPPORTED_MODEL:" + snapshot.model());
        if (snapshot.manifest() == null) {
            errors.add("MISSING_MANIFEST");
        } else {
            snapshot.manifest().supportedRiskLevels().forEach(level -> {
                if (!RISK_LEVELS.contains(level)) errors.add("UNKNOWN_RISK_LEVEL:" + level);
            });
            if (snapshot.manifest().outputSchema().isBlank()) errors.add("MISSING_OUTPUT_SCHEMA");
        }
        if (snapshot.toolPolicy() == null) {
            errors.add("MISSING_TOOL_POLICY");
        } else {
            for (AgentToolGrant grant : snapshot.toolPolicy().grants()) {
                if (!KNOWN_TOOLS.contains(grant.toolName())) errors.add("UNKNOWN_TOOL:" + grant.toolName());
                if (MCP_TOOLS.contains(grant.toolName()) && !snapshot.toolPolicy().mcpServers().contains("tavily")) {
                    errors.add("MCP_TOOL_WITHOUT_SERVER:" + grant.toolName());
                }
            }
            snapshot.toolPolicy().mcpServers().forEach(server -> {
                if (!KNOWN_MCP.contains(server)) errors.add("UNKNOWN_MCP_SERVER:" + server);
            });
        }
        if (snapshot.status() == null) errors.add("MISSING_STATUS");
        return new AgentManifestValidation(errors, List.of());
    }
}
