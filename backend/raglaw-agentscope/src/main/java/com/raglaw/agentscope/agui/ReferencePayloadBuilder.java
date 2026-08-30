package com.raglaw.agentscope.agui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.retrieval.CitationRelevanceFilter;
import com.raglaw.rag.retrieval.ReferenceExcerptEnhancer;
import com.raglaw.rag.retrieval.chunk.ChunkHeadingHeuristics;
import com.raglaw.rag.tool.RagSearchHit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Single source of truth for reference payloads: SSE {@code reference} events and {@code citationsJson}.
 */
@Component
public class ReferencePayloadBuilder {

    public static final String CATALOG_SUMMARY_CHUNK_ID = "catalog-summary";

    private final DocumentRepository documentRepository;
    private final ReferenceExcerptEnhancer referenceExcerptEnhancer;
    private final CitationRelevanceFilter citationRelevanceFilter;
    private final ObjectMapper objectMapper;

    public ReferencePayloadBuilder(
            DocumentRepository documentRepository,
            ReferenceExcerptEnhancer referenceExcerptEnhancer,
            CitationRelevanceFilter citationRelevanceFilter,
            ObjectMapper objectMapper
    ) {
        this.documentRepository = documentRepository;
        this.referenceExcerptEnhancer = referenceExcerptEnhancer;
        this.citationRelevanceFilter = citationRelevanceFilter;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> buildWebPayload(WebReference webReference) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("index", webReference.index());
        payload.put("chunkId", webReference.chunkId());
        payload.put("documentId", "");
        payload.put("path", webReference.url());
        payload.put("excerpt", webReference.excerpt());
        payload.put("content", webReference.excerpt());
        payload.put("score", 0.0);
        payload.put("title", webReference.title());
        payload.put("source", "web");
        return payload;
    }

    public String serializeJson(List<RagSearchHit> hits, List<WebReference> webRefs, String userQuery) {
        List<Map<String, Object>> payloads = new ArrayList<>();
        payloads.addAll(buildUserVisiblePayloads(hits, userQuery));
        if (webRefs != null) {
            for (WebReference webRef : webRefs) {
                payloads.add(buildWebPayload(webRef));
            }
        }
        if (payloads.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payloads);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize references", e);
        }
    }

    public String serializeJson(List<RagSearchHit> hits, List<WebReference> webRefs) {
        return serializeJson(hits, webRefs, null);
    }

    public String serializeJson(List<RagSearchHit> hits) {
        return serializeJson(hits, List.of(), null);
    }

    public List<Map<String, Object>> buildPayloads(List<RagSearchHit> hits) {
        return buildPayloads(hits, null);
    }

    public List<Map<String, Object>> buildPayloads(List<RagSearchHit> hits, String userQuery) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> payloads = new ArrayList<>();
        for (int i = 0; i < hits.size(); i++) {
            payloads.add(buildPayload(hits.get(i), i + 1, userQuery));
        }
        return payloads;
    }

    /**
     * Payloads for SSE {@code reference} events and persisted {@code citations_json}.
     * Excludes synthetic catalog overview rows and renumbers from 1.
     */
    public List<Map<String, Object>> buildUserVisiblePayloads(List<RagSearchHit> hits, String userQuery) {
        return buildPayloads(relevantCitableHits(hits, userQuery), userQuery);
    }

    public List<RagSearchHit> relevantCitableHits(List<RagSearchHit> hits, String userQuery) {
        return citationRelevanceFilter.filter(citableHits(hits), userQuery);
    }

    public static List<RagSearchHit> citableHits(List<RagSearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<RagSearchHit> result = new ArrayList<>();
        for (RagSearchHit hit : hits) {
            if (isUserVisibleHit(hit)) {
                result.add(hit);
            }
        }
        return result;
    }

    public static int countCitableKnowledgeHits(List<RagSearchHit> hits) {
        return citableHits(hits).size();
    }

    public static boolean isUserVisibleHit(RagSearchHit hit) {
        return hit != null
                && hit.chunkId() != null
                && !CATALOG_SUMMARY_CHUNK_ID.equals(hit.chunkId());
    }

    public Map<String, Object> buildPayload(RagSearchHit hit, int index) {
        return buildPayload(hit, index, null);
    }

    public Map<String, Object> buildPayload(RagSearchHit hit, int index, String userQuery) {
        String excerpt = isCatalogHit(hit)
                ? (hit.excerpt() != null ? hit.excerpt() : "")
                : referenceExcerptEnhancer.enhance(
                        hit.excerpt(),
                        hit.documentId(),
                        userQuery
                );
        Map<String, Object> payload = new HashMap<>();
        payload.put("index", index);
        payload.put("chunkId", hit.chunkId());
        payload.put("documentId", hit.documentId() != null ? hit.documentId() : "");
        payload.put("path", hit.l1L2L3Path());
        payload.put("excerpt", excerpt);
        payload.put("content", hit.llmContentOrExcerpt());
        payload.put("score", hit.score());
        payload.put("title", resolveTitle(hit));
        payload.put("source", "knowledge");
        String articleLabel = ChunkHeadingHeuristics.extractArticleAnchor(excerpt);
        if (articleLabel.isBlank()) {
            articleLabel = ChunkHeadingHeuristics.extractArticleAnchor(hit.llmContentOrExcerpt());
        }
        if (articleLabel.isBlank()) {
            articleLabel = ChunkHeadingHeuristics.extractArticleAnchor(hit.excerpt());
        }
        if (!articleLabel.isBlank()) {
            payload.put("articleLabel", articleLabel);
        }
        return payload;
    }

    private static boolean isCatalogHit(RagSearchHit hit) {
        return hit.chunkId() != null && hit.chunkId().startsWith("catalog-");
    }

    private String resolveTitle(RagSearchHit hit) {
        if (hit.documentId() != null && !hit.documentId().isBlank()) {
            return documentRepository.findById(hit.documentId())
                    .map(DocumentEntity::getTitle)
                    .filter(title -> title != null && !title.isBlank())
                    .orElse(hit.l1L2L3Path());
        }
        return hit.l1L2L3Path();
    }
}
