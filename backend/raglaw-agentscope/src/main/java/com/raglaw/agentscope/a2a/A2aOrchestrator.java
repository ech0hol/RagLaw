package com.raglaw.agentscope.a2a;

import com.raglaw.agentadmin.model.AgentConfigSnapshot;
import com.raglaw.agentadmin.registry.AgentRegistry;
import com.raglaw.agentscope.agui.AguiSseWriter;
import com.raglaw.agentscope.trace.TraceRecorder;
import com.raglaw.rag.tool.RagSearchHit;
import com.raglaw.rag.tool.RagSearchTool;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class A2aOrchestrator {

    private static final Set<String> CONTRACT_KEYWORDS = Set.of(
            "合同", "违约", "条款", "协议", "租赁", "借款", "担保"
    );
    private static final Set<String> CASE_KEYWORDS = Set.of(
            "案例", "判决", "裁判", "法院", "胜诉", "败诉"
    );

    private final AgentRegistry agentRegistry;
    private final RagSearchTool ragSearchTool;
    private final TraceRecorder traceRecorder;

    public A2aOrchestrator(
            AgentRegistry agentRegistry,
            RagSearchTool ragSearchTool,
            TraceRecorder traceRecorder
    ) {
        this.agentRegistry = agentRegistry;
        this.ragSearchTool = ragSearchTool;
        this.traceRecorder = traceRecorder;
    }

    public A2aResult delegate(
            AgentConfigSnapshot generalAgent,
            String userMessage,
            String traceId,
            SseEmitter emitter
    ) throws IOException {
        List<String> peers = generalAgent.a2aPeers();
        if (peers == null || peers.isEmpty()) {
            return A2aResult.empty();
        }

        String peerCode = selectPeer(peers, userMessage);
        AgentConfigSnapshot peer = agentRegistry.get(peerCode);
        if (peer == null || !peer.tools().contains("rag_search")) {
            return A2aResult.empty();
        }

        long startMs = System.currentTimeMillis();
        AguiSseWriter.send(emitter, "status", Map.of(
                "message", statusMessage(peer),
                "peerAgent", peer.code()
        ));

        List<RagSearchHit> hits = ragSearchTool.search(
                userMessage,
                peer.knowledgeScopes(),
                5,
                peer.code()
        );

        long latency = System.currentTimeMillis() - startMs;
        String outputSummary = hits.isEmpty()
                ? "未检索到相关片段"
                : "检索到 " + hits.size() + " 条依据";
        traceRecorder.recordA2aCall(
                traceId,
                generalAgent.code(),
                peer.code(),
                userMessage,
                outputSummary,
                latency
        );
        traceRecorder.recordStage(
                traceId,
                "a2a_delegate",
                Map.of("peer", peer.code(), "hitCount", hits.size()),
                latency
        );

        return new A2aResult(peer.code(), peer.name(), hits);
    }

    private static String selectPeer(List<String> peers, String query) {
        String normalized = query == null ? "" : query;
        if (containsAny(normalized, CONTRACT_KEYWORDS)) {
            return pickPeer(peers, "CONTRACT");
        }
        if (containsAny(normalized, CASE_KEYWORDS)) {
            return pickPeer(peers, "CASE");
        }
        return pickPeer(peers, "STATUTE");
    }

    private static boolean containsAny(String text, Set<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String pickPeer(List<String> peers, String typePrefix) {
        String upper = typePrefix.toUpperCase(Locale.ROOT);
        for (String peer : peers) {
            if (peer != null && peer.toUpperCase(Locale.ROOT).startsWith(upper)) {
                return peer;
            }
        }
        return peers.get(0);
    }

    private static String statusMessage(AgentConfigSnapshot peer) {
        return switch (peer.type()) {
            case "STATUTE" -> "正在咨询法规助手…";
            case "CASE" -> "正在咨询案例助手…";
            case "CONTRACT" -> "正在咨询合同审查助手…";
            default -> "正在咨询" + peer.name() + "…";
        };
    }

    public record A2aResult(String peerCode, String peerName, List<RagSearchHit> hits) {
        public static A2aResult empty() {
            return new A2aResult(null, null, List.of());
        }

        public boolean hasPeer() {
            return peerCode != null && !peerCode.isBlank();
        }
    }
}
