package com.raglaw.agentscope.expert;

import com.raglaw.agentadmin.model.AgentConfigSnapshot;
import com.raglaw.agentadmin.registry.AgentRegistry;
import com.raglaw.agentscope.a2a.A2aPeerSelector;
import com.raglaw.agentscope.a2a.PeerSelection;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ExpertRouter {

    private static final String GENERAL_CODE = "GENERAL";

    private final A2aPeerSelector peerSelector;
    private final AgentRegistry agentRegistry;

    public ExpertRouter(A2aPeerSelector peerSelector, AgentRegistry agentRegistry) {
        this.peerSelector = peerSelector;
        this.agentRegistry = agentRegistry;
    }

    public ExpertContext resolve(AgentConfigSnapshot agent, String query, String contextDocumentId) {
        if (!GENERAL_CODE.equals(agent.code())) {
            return ExpertContext.forDirectAgent(agent, contextDocumentId);
        }

        List<String> peers = agent.a2aPeers();
        if (peers == null || peers.isEmpty()) {
            return ExpertContext.forGeneral(agent, contextDocumentId);
        }

        PeerSelection selection = peerSelector.select(peers, query);
        AgentConfigSnapshot peer = agentRegistry.get(selection.peerCode());
        if (peer == null || !peer.tools().contains("rag_search")) {
            return ExpertContext.forGeneral(agent, contextDocumentId);
        }

        return ExpertContext.delegated(
                agent,
                peer,
                selection.reason(),
                selection.usedLlm(),
                selection.lowConfidence(),
                contextDocumentId
        );
    }
}
