package com.raglaw.rag.service;

import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.dto.KnowledgeHitDto;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.retrieval.FullTextScoreFilter;
import com.raglaw.rag.retrieval.FullTextSnippetExtractor;
import com.raglaw.rag.retrieval.QueryRewriteService;
import com.raglaw.rag.retrieval.RetrievalConstraints;
import com.raglaw.rag.retrieval.funnel.FunnelDegradationPolicy;
import com.raglaw.rag.retrieval.funnel.KnowledgeFunnelRouter;
import com.raglaw.rag.search.ElasticsearchIndexService;
import com.raglaw.rag.search.ElasticsearchRetriever;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class DocumentKnowledgeSearchService {

    private static final String KNOWLEDGE_API_ROUTE = "knowledge_api";

    private final DocumentRepository documentRepository;
    private final KnowledgeScopeResolver knowledgeScopeResolver;
    private final KnowledgeFunnelRouter knowledgeFunnelRouter;
    private final QueryRewriteService queryRewriteService;
    private final FullTextScoreFilter fullTextScoreFilter;
    private final RagProperties ragProperties;
    private final ObjectProvider<ElasticsearchRetriever> elasticsearchRetrieverProvider;
    private final CategoryService categoryService;

    public DocumentKnowledgeSearchService(
            DocumentRepository documentRepository,
            KnowledgeScopeResolver knowledgeScopeResolver,
            KnowledgeFunnelRouter knowledgeFunnelRouter,
            QueryRewriteService queryRewriteService,
            FullTextScoreFilter fullTextScoreFilter,
            RagProperties ragProperties,
            ObjectProvider<ElasticsearchRetriever> elasticsearchRetrieverProvider,
            CategoryService categoryService
    ) {
        this.documentRepository = documentRepository;
        this.knowledgeScopeResolver = knowledgeScopeResolver;
        this.knowledgeFunnelRouter = knowledgeFunnelRouter;
        this.queryRewriteService = queryRewriteService;
        this.fullTextScoreFilter = fullTextScoreFilter;
        this.ragProperties = ragProperties;
        this.elasticsearchRetrieverProvider = elasticsearchRetrieverProvider;
        this.categoryService = categoryService;
    }

    public List<KnowledgeHitDto> search(
            String query,
            List<String> knowledgeScopes,
            String docType,
            String l2Path,
            int limit
    ) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<String> scopePaths = knowledgeScopeResolver.resolvePaths(knowledgeScopes);
        RetrievalConstraints constraints = resolveConstraints(query, scopePaths);
        List<String> effectiveScopes = constraints.effectiveScopePaths();
        if (effectiveScopes.isEmpty() && !scopePaths.isEmpty()) {
            effectiveScopes = scopePaths;
        }

        String searchQuery = queryRewriteService.rewrite(query);
        ElasticsearchRetriever elasticsearchRetriever = elasticsearchRetrieverProvider.getIfAvailable();
        List<FullTextScoreFilter.ScoredRow> scored;
        if (elasticsearchRetriever != null && elasticsearchRetriever.isEnabled()) {
            scored = dedupeScoredRowsFromEs(
                    elasticsearchRetriever.searchDocumentsBm25(
                            searchQuery,
                            effectiveScopes,
                            Math.max(limit, 1) * 4
                    )
            );
        } else {
            List<Object[]> rows = documentRepository.searchFullTextDocuments(
                    searchQuery,
                    effectiveScopes,
                    effectiveScopes.size(),
                    List.of(),
                    0,
                    Math.max(limit, 1) * 4
            );
            scored = dedupeScoredRows(rows);
        }
        scored = fullTextScoreFilter.filter(scored);

        List<String> documentIds = scored.stream().map(FullTextScoreFilter.ScoredRow::documentId).toList();
        Map<String, DocumentEntity> documents = new LinkedHashMap<>();
        if (!documentIds.isEmpty()) {
            documentRepository.findAllById(documentIds).forEach(doc -> documents.put(doc.getId(), doc));
        }

        Map<String, KnowledgeHitDto> byNormalizedTitle = new LinkedHashMap<>();
        for (FullTextScoreFilter.ScoredRow row : scored) {
            DocumentEntity doc = documents.get(row.documentId());
            if (doc == null) {
                continue;
            }
            String path = row.categoryPath() != null ? row.categoryPath() : categoryPathFor(doc);
            if (docType != null && !docType.isBlank() && !path.contains("/" + docType)) {
                continue;
            }
            if (l2Path != null && !l2Path.isBlank() && !path.startsWith(l2Path)) {
                continue;
            }
            String excerpt = FullTextSnippetExtractor.extract(
                    doc.getFullText(),
                    searchQuery,
                    ragProperties.getRetrieval().getDisplayExcerptMaxChars()
            );
            KnowledgeHitDto hit = new KnowledgeHitDto(
                    doc.getId(),
                    doc.getId(),
                    doc.getTitle(),
                    path,
                    excerpt,
                    row.score(),
                    doc.getCreatedAt(),
                    parseEffectiveDate(doc)
            );
            String titleKey = normalizeTitle(hit.title());
            KnowledgeHitDto existing = byNormalizedTitle.get(titleKey);
            if (existing == null || hit.score() > existing.score()) {
                byNormalizedTitle.put(titleKey, hit);
            }
            if (byNormalizedTitle.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(byNormalizedTitle.values());
    }

    private static List<FullTextScoreFilter.ScoredRow> dedupeScoredRowsFromEs(
            List<ElasticsearchIndexService.DocumentSearchHit> hits
    ) {
        Map<String, FullTextScoreFilter.ScoredRow> byDocumentId = new LinkedHashMap<>();
        for (ElasticsearchIndexService.DocumentSearchHit hit : hits) {
            FullTextScoreFilter.ScoredRow candidate = new FullTextScoreFilter.ScoredRow(
                    hit.documentId(),
                    null,
                    hit.score()
            );
            FullTextScoreFilter.ScoredRow existing = byDocumentId.get(hit.documentId());
            if (existing == null || candidate.score() > existing.score()) {
                byDocumentId.put(hit.documentId(), candidate);
            }
        }
        return new ArrayList<>(byDocumentId.values());
    }

    private String categoryPathFor(DocumentEntity doc) {
        try {
            return categoryService.findEntity(doc.getCategoryId()).getPath();
        } catch (Exception ex) {
            return "";
        }
    }

    private static List<FullTextScoreFilter.ScoredRow> dedupeScoredRows(List<Object[]> rows) {
        Map<String, FullTextScoreFilter.ScoredRow> byDocumentId = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String documentId = (String) row[0];
            double score = row[2] instanceof Number number ? number.doubleValue() : 0.0;
            FullTextScoreFilter.ScoredRow candidate = new FullTextScoreFilter.ScoredRow(
                    documentId,
                    (String) row[1],
                    score
            );
            FullTextScoreFilter.ScoredRow existing = byDocumentId.get(documentId);
            if (existing == null || candidate.score() > existing.score()) {
                byDocumentId.put(documentId, candidate);
            }
        }
        return new ArrayList<>(byDocumentId.values());
    }

    private static String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "";
        }
        return title.replaceAll("_\\d{8}$", "").trim();
    }

    private RetrievalConstraints resolveConstraints(String query, List<String> scopePaths) {
        if (!ragProperties.getKnowledgeSearch().isUseFunnel()
                || !ragProperties.getRetrieval().isFunnelEnabled()) {
            return RetrievalConstraints.empty();
        }
        FunnelDegradationPolicy.AppliedFunnel applied = knowledgeFunnelRouter.route(
                query,
                scopePaths,
                null,
                false,
                KNOWLEDGE_API_ROUTE
        );
        return new RetrievalConstraints(
                applied.retrievalScopePaths(),
                applied.retrievalTopicPaths(),
                List.of()
        );
    }

    private static String parseEffectiveDate(DocumentEntity doc) {
        if (doc.getMetadataJson() == null || doc.getMetadataJson().isBlank()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(doc.getMetadataJson());
            String value = root.path("effectiveDate").asText("");
            return value.isBlank() ? null : value;
        } catch (Exception ex) {
            return null;
        }
    }
}
