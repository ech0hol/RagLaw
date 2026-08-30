package com.raglaw.rag.tool;

import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.retrieval.HybridRetriever;
import com.raglaw.rag.retrieval.RetrievalConstraints;
import com.raglaw.rag.retrieval.RetrievalContextEnricher;
import com.raglaw.rag.retrieval.RetrievalSearchResult;
import com.raglaw.rag.retrieval.funnel.FunnelDegradationPolicy;
import com.raglaw.rag.retrieval.funnel.FunnelResult;
import com.raglaw.rag.retrieval.funnel.KnowledgeFunnelRouter;
import com.raglaw.rag.service.KnowledgeCatalogService;
import com.raglaw.rag.service.KnowledgeScopeResolver;
import com.raglaw.rag.tool.CatalogQueryDetector;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class HybridRagSearchTool implements RagSearchTool {

    private final HybridRetriever hybridRetriever;
    private final KnowledgeScopeResolver knowledgeScopeResolver;
    private final RetrievalContextEnricher retrievalContextEnricher;
    private final KnowledgeFunnelRouter knowledgeFunnelRouter;
    private final RagProperties ragProperties;
    private final KnowledgeCatalogService knowledgeCatalogService;

    public HybridRagSearchTool(
            HybridRetriever hybridRetriever,
            KnowledgeScopeResolver knowledgeScopeResolver,
            RetrievalContextEnricher retrievalContextEnricher,
            KnowledgeFunnelRouter knowledgeFunnelRouter,
            RagProperties ragProperties,
            KnowledgeCatalogService knowledgeCatalogService
    ) {
        this.hybridRetriever = hybridRetriever;
        this.knowledgeScopeResolver = knowledgeScopeResolver;
        this.retrievalContextEnricher = retrievalContextEnricher;
        this.knowledgeFunnelRouter = knowledgeFunnelRouter;
        this.ragProperties = ragProperties;
        this.knowledgeCatalogService = knowledgeCatalogService;
    }

    @Override
    public List<RagSearchHit> search(String query, List<String> knowledgeScopes, int limit, String agentCode) {
        return searchDetailed(query, knowledgeScopes, limit, agentCode, null, false, "").hits();
    }

    @Override
    public RagSearchResult searchDetailed(
            String query,
            List<String> knowledgeScopes,
            int limit,
            String agentCode,
            String documentId,
            boolean lowConfidence,
            String scopeRouteReason
    ) {
        List<String> scopePaths = knowledgeScopeResolver.resolvePaths(knowledgeScopes);
        if (CatalogQueryDetector.isCatalogQuery(query)) {
            List<RagSearchHit> catalogHits = knowledgeCatalogService.buildCatalogHits(scopePaths, limit);
            Map<String, Object> trace = new HashMap<>();
            trace.put("catalogMode", true);
            trace.put("catalogHitCount", catalogHits.size());
            return new RagSearchResult(catalogHits, null, trace);
        }

        FunnelDegradationPolicy.AppliedFunnel applied;
        if (ragProperties.getRetrieval().isFunnelEnabled()) {
            applied = knowledgeFunnelRouter.route(
                    query,
                    scopePaths,
                    documentId,
                    lowConfidence,
                    scopeRouteReason
            );
        } else {
            FunnelResult fallback = documentId != null && !documentId.isBlank()
                    ? FunnelResult.pinned(scopePaths, documentId, scopeRouteReason, lowConfidence)
                    : new FunnelResult(
                            scopePaths,
                            List.of(),
                            List.of(),
                            0.0,
                            0.0,
                            "SCOPE",
                            scopeRouteReason,
                            lowConfidence,
                            List.of()
                    );
            applied = new FunnelDegradationPolicy.AppliedFunnel(
                    fallback,
                    scopePaths,
                    List.of(),
                    documentId != null && !documentId.isBlank() ? List.of(documentId) : List.of()
            );
        }

        RetrievalConstraints constraints = new RetrievalConstraints(
                applied.retrievalScopePaths(),
                applied.retrievalTopicPaths(),
                applied.retrievalDocumentIds()
        );
        RetrievalSearchResult retrieval = hybridRetriever.search(
                query,
                scopePaths,
                limit,
                agentCode,
                documentId,
                constraints
        );
        List<RagSearchHit> hits = retrieval.hits().stream()
                .map(retrievalContextEnricher::toSearchHit)
                .toList();

        Map<String, Object> trace = new HashMap<>();
        trace.put("fulltextCount", retrieval.fulltextCount());
        trace.put("vectorCount", retrieval.vectorCount());
        trace.put("fulltextAfterFilter", retrieval.fulltextAfterFilter());
        trace.put("vectorAfterFilter", retrieval.vectorAfterFilter());
        trace.put("rrfCount", retrieval.rrfCount());

        return new RagSearchResult(hits, applied.funnelResult(), trace);
    }

    @Override
    public List<RagSearchHit> searchScopedToDocument(
            String query,
            List<String> knowledgeScopes,
            int limit,
            String agentCode,
            String documentId
    ) {
        return searchDetailed(query, knowledgeScopes, limit, agentCode, documentId, false, "scoped_document").hits();
    }

    List<RagSearchHit> toHitsForTest(List<com.raglaw.rag.dto.RetrievalHit> hits) {
        return hits.stream().map(retrievalContextEnricher::toSearchHit).toList();
    }
}
