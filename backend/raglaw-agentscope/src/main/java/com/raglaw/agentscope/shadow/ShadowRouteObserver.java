package com.raglaw.agentscope.shadow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentscope.a2a.A2aPeerSelector;
import com.raglaw.agentscope.a2a.A2aRoutingLlm;
import com.raglaw.agentscope.a2a.PeerSelection;
import com.raglaw.agentscope.domain.ShadowRouteLogEntity;
import com.raglaw.agentscope.domain.ShadowRouteLogRepository;
import com.raglaw.rag.retrieval.funnel.DocumentCandidate;
import com.raglaw.rag.retrieval.funnel.FunnelResult;
import com.raglaw.rag.retrieval.funnel.KnowledgeFunnelRouter;
import com.raglaw.rag.service.KnowledgeScopeResolver;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ShadowRouteObserver {

    private static final Logger log = LoggerFactory.getLogger(ShadowRouteObserver.class);

    private final KnowledgeFunnelRouter knowledgeFunnelRouter;
    private final KnowledgeScopeResolver knowledgeScopeResolver;
    private final A2aPeerSelector peerSelector;
    private final A2aRoutingLlm routingLlm;
    private final ShadowRouteLogRepository shadowRouteLogRepository;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ShadowRouteObserver(
            KnowledgeFunnelRouter knowledgeFunnelRouter,
            KnowledgeScopeResolver knowledgeScopeResolver,
            A2aPeerSelector peerSelector,
            A2aRoutingLlm routingLlm,
            ShadowRouteLogRepository shadowRouteLogRepository,
            ObjectMapper objectMapper
    ) {
        this.knowledgeFunnelRouter = knowledgeFunnelRouter;
        this.knowledgeScopeResolver = knowledgeScopeResolver;
        this.peerSelector = peerSelector;
        this.routingLlm = routingLlm;
        this.shadowRouteLogRepository = shadowRouteLogRepository;
        this.objectMapper = objectMapper;
    }

    public void observeAsync(
            String traceId,
            String query,
            List<String> knowledgeScopes,
            String contextDocumentId,
            boolean lowConfidence,
            String scopeRouteReason,
            List<String> a2aPeers
    ) {
        executor.execute(() -> {
            try {
                observeA2a(traceId, query, a2aPeers);
            } catch (Exception e) {
                log.warn("Shadow route observation failed for trace {}", traceId, e);
            }
        });
    }

    public void observeDocumentFromFunnel(
            String traceId,
            String contextDocumentId,
            FunnelResult funnelResult
    ) {
        if (funnelResult == null || funnelResult.documentRanking().isEmpty()) {
            return;
        }
        DocumentCandidate top = funnelResult.documentRanking().get(0);
        boolean hit = contextDocumentId != null && contextDocumentId.equals(top.documentId());
        int rank = findRank(funnelResult.documentRanking(), contextDocumentId);
        save(
                traceId,
                "DOCUMENT",
                contextDocumentId,
                top.documentId(),
                hit,
                rank,
                Map.of(
                        "documentConfidence", funnelResult.documentConfidence(),
                        "topicConfidence", funnelResult.topicConfidence(),
                        "topTitle", top.title()
                )
        );
    }

    private void observeDocument(
            String traceId,
            String query,
            List<String> knowledgeScopes,
            String contextDocumentId,
            boolean lowConfidence,
            String scopeRouteReason
    ) {
        List<String> scopePaths = knowledgeScopeResolver.resolvePaths(knowledgeScopes);
        var applied = knowledgeFunnelRouter.route(query, scopePaths, null, lowConfidence, scopeRouteReason);
        FunnelResult funnel = applied.funnelResult();
        if (funnel.documentRanking().isEmpty()) {
            return;
        }
        DocumentCandidate top = funnel.documentRanking().get(0);
        boolean hit = contextDocumentId != null && contextDocumentId.equals(top.documentId());
        int rank = findRank(funnel.documentRanking(), contextDocumentId);
        save(
                traceId,
                "DOCUMENT",
                contextDocumentId,
                top.documentId(),
                hit,
                rank,
                Map.of(
                        "documentConfidence", funnel.documentConfidence(),
                        "topicConfidence", funnel.topicConfidence(),
                        "degradation", funnel.degradationLevel()
                )
        );
    }

    private void observeA2a(String traceId, String query, List<String> peers) {
        if (peers == null || peers.isEmpty()) {
            return;
        }
        PeerSelection rule = peerSelector.select(peers, query);
        String llmPeer = routingLlm.pickPeer(peers, query).orElse("");
        boolean agreement = rule.peerCode().equals(llmPeer);
        save(traceId, "A2A", rule.peerCode(), llmPeer, agreement, null, Map.of(
                "ruleReason", rule.reason(),
                "usedLlm", rule.usedLlm(),
                "lowConfidence", rule.lowConfidence()
        ));
    }

    private static int findRank(List<DocumentCandidate> ranking, String documentId) {
        if (documentId == null || documentId.isBlank()) {
            return -1;
        }
        for (int i = 0; i < ranking.size(); i++) {
            if (documentId.equals(ranking.get(i).documentId())) {
                return i + 1;
            }
        }
        return -1;
    }

    private void save(
            String traceId,
            String shadowType,
            String userValue,
            String systemValue,
            Boolean hit,
            Integer rank,
            Map<String, Object> confidence
    ) {
        ShadowRouteLogEntity entity = new ShadowRouteLogEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setTraceId(traceId);
        entity.setShadowType(shadowType);
        entity.setUserValue(userValue);
        entity.setSystemValue(systemValue);
        entity.setHit(hit);
        entity.setRank(rank);
        entity.setConfidenceJson(writeJson(confidence));
        entity.setCreatedAt(Instant.now());
        try {
            shadowRouteLogRepository.save(entity);
        } catch (Exception e) {
            log.warn("Failed to persist shadow route log for trace {}", traceId, e);
        }
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }
}
