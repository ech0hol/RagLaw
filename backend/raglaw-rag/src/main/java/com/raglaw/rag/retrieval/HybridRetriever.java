package com.raglaw.rag.retrieval;

import com.raglaw.rag.domain.DocumentChunkEntity;
import com.raglaw.rag.dto.RetrievalHit;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.service.EmbeddingService;
import com.raglaw.rag.service.VectorStoreService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class HybridRetriever {

    private final DocumentChunkRepository documentChunkRepository;
    private final EmbeddingService embeddingService;
    private final ObjectProvider<VectorStoreService> vectorStoreProvider;
    private final RetrievalReranker retrievalReranker;

    public HybridRetriever(
            DocumentChunkRepository documentChunkRepository,
            EmbeddingService embeddingService,
            ObjectProvider<VectorStoreService> vectorStoreProvider,
            RetrievalReranker retrievalReranker
    ) {
        this.documentChunkRepository = documentChunkRepository;
        this.embeddingService = embeddingService;
        this.vectorStoreProvider = vectorStoreProvider;
        this.retrievalReranker = retrievalReranker;
    }

    public List<RetrievalHit> search(String query, List<String> scopePaths, int limit) {
        return search(query, scopePaths, limit, null, null);
    }

    public List<RetrievalHit> search(String query, List<String> scopePaths, int limit, String agentCode) {
        return search(query, scopePaths, limit, agentCode, null);
    }

    public List<RetrievalHit> search(
            String query,
            List<String> scopePaths,
            int limit,
            String agentCode,
            String documentId
    ) {
        List<String> scopes = scopePaths == null ? List.of() : scopePaths;
        if (documentId != null && !documentId.isBlank()) {
            List<RetrievalHit> documentHits = toHits(
                    documentChunkRepository.searchFullTextForDocument(query, documentId, limit));
            return retrievalReranker.rerank(documentHits, agentCode).stream().limit(limit).toList();
        }

        List<RetrievalHit> fulltextHits = toHits(
                documentChunkRepository.searchFullText(query, scopes, scopes.size(), limit));

        VectorStoreService vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null || !vectorStore.isEnabled() || !embeddingService.isEnabled()) {
            return retrievalReranker.rerank(fulltextHits, agentCode).stream().limit(limit).toList();
        }

        List<RetrievalHit> vectorHits = embeddingService.embed(query)
                .map(vector -> toHitsFromVector(vectorStore.search(vector, scopes, limit), vectorStore))
                .orElse(List.of());

        if (vectorHits.isEmpty()) {
            return retrievalReranker.rerank(fulltextHits, agentCode).stream().limit(limit).toList();
        }
        List<RetrievalHit> fused = RrfFusion.fuse(List.of(fulltextHits, vectorHits), RrfFusion.DEFAULT_K, limit);
        return retrievalReranker.rerank(fused, agentCode).stream().limit(limit).toList();
    }

    private List<RetrievalHit> toHits(List<Object[]> rows) {
        List<RetrievalHit> hits = new ArrayList<>();
        for (Object[] row : rows) {
            hits.add(new RetrievalHit(
                    (String) row[0],
                    (String) row[1],
                    (String) row[2],
                    (String) row[3],
                    (String) row[4],
                    (String) row[5],
                    row[6] instanceof Number n ? n.doubleValue() : 0.0
            ));
        }
        return hits;
    }

    private List<RetrievalHit> toHitsFromVector(
            List<VectorStoreService.VectorHit> vectorHits,
            VectorStoreService vectorStore
    ) {
        if (vectorHits.isEmpty()) {
            return List.of();
        }
        List<String> chunkIds = vectorHits.stream().map(VectorStoreService.VectorHit::chunkId).toList();
        Map<String, DocumentChunkEntity> chunks = documentChunkRepository.findAllById(chunkIds).stream()
                .collect(Collectors.toMap(DocumentChunkEntity::getId, Function.identity()));
        List<RetrievalHit> hits = new ArrayList<>();
        for (VectorStoreService.VectorHit vectorHit : vectorHits) {
            DocumentChunkEntity chunk = chunks.get(vectorHit.chunkId());
            if (chunk != null) {
                hits.add(new RetrievalHit(
                        chunk.getId(),
                        chunk.getDocumentId(),
                        chunk.getContent(),
                        chunk.getL1Path(),
                        chunk.getL2Path(),
                        chunk.getL3Path(),
                        vectorHit.score()
                ));
            }
        }
        return hits;
    }
}
