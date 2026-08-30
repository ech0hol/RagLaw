package com.raglaw.agentscope.expert;

import com.raglaw.agentadmin.model.AgentConfigSnapshot;
import java.util.List;

public record ExpertContext(
        String peerCode,
        String peerName,
        String systemPrompt,
        List<String> knowledgeScopes,
        String searchAgentCode,
        String routeReason,
        boolean usedLlm,
        boolean lowConfidence,
        String contextDocumentId,
        boolean delegated,
        List<String> tools,
        List<String> mcpServers
) {

    public static ExpertContext forDirectAgent(AgentConfigSnapshot agent, String contextDocumentId) {
        return new ExpertContext(
                agent.code(),
                agent.name(),
                agent.systemPrompt(),
                agent.knowledgeScopes(),
                agent.code(),
                "direct:" + agent.code(),
                false,
                false,
                contextDocumentId,
                false,
                agent.tools(),
                agent.mcpServers()
        );
    }

    public static ExpertContext forGeneral(AgentConfigSnapshot general, String contextDocumentId) {
        return new ExpertContext(
                general.code(),
                general.name(),
                general.systemPrompt(),
                general.knowledgeScopes(),
                general.code(),
                "general",
                false,
                false,
                contextDocumentId,
                false,
                general.tools(),
                general.mcpServers()
        );
    }

    public static ExpertContext delegated(
            AgentConfigSnapshot general,
            AgentConfigSnapshot peer,
            String routeReason,
            boolean usedLlm,
            boolean lowConfidence,
            String contextDocumentId
    ) {
        String combinedPrompt = peer.systemPrompt() + "\n\n" + general.systemPrompt();
        return new ExpertContext(
                peer.code(),
                peer.name(),
                combinedPrompt,
                peer.knowledgeScopes(),
                peer.code(),
                routeReason,
                usedLlm,
                lowConfidence,
                contextDocumentId,
                true,
                peer.tools(),
                peer.mcpServers()
        );
    }
}
