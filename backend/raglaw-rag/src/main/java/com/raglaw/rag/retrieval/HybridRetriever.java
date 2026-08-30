package com.raglaw.rag.retrieval;

import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.dto.RetrievalHit;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.search.ElasticsearchRetriever;
import com.raglaw.rag.search.EsSearchResult;
import com.raglaw.rag.service.EmbeddingService;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class HybridRetriever {

    private static final Logger log = LoggerFactory.getLogger(HybridRetriever.class);

    private final DocumentChunkRepository documentChunkRepository;
    private final EmbeddingService embeddingService;
    private final ObjectProvider<ElasticsearchRetriever> elasticsearchRetrieverProvider;
    private final RetrievalReranker retrievalReranker;
    private final RetrievalDiversifier retrievalDiversifier;
    private final QueryRewriteService queryRewriteService;
    private final RagProperties ragProperties;
    private final ChannelThresholdFilter channelThresholdFilter;
    private final RetrievalSubstantiveFilter retrievalSubstantiveFilter;

    public HybridRetriever(
            DocumentChunkRepository documentChunkRepository,
            EmbeddingService embeddingService,
            ObjectProvider<ElasticsearchRetriever> elasticsearchRetrieverProvider,
            RetrievalReranker retrievalReranker,
            RetrievalDiversifier retrievalDiversifier,
            QueryRewriteService queryRewriteService,
            RagProperties ragProperties,
            ChannelThresholdFilter channelThresholdFilter,
            RetrievalSubstantiveFilter retrievalSubstantiveFilter
    ) {
        this.documentChunkRepository = documentChunkRepository;
        this.embeddingService = embeddingService;
        this.elasticsearchRetrieverProvider = elasticsearchRetrieverProvider;
        this.retrievalReranker = retrievalReranker;
        this.retrievalDiversifier = retrievalDiversifier;
        this.queryRewriteService = queryRewriteService;
        this.ragProperties = ragProperties;
        this.channelThresholdFilter = channelThresholdFilter;
        this.retrievalSubstantiveFilter = retrievalSubstantiveFilter;
    }

    public List<RetrievalHit> search(String query, List<String> scopePaths, int limit) {
        return search(query, scopePaths, limit, null, null, RetrievalConstraints.empty()).hits();
    }

    public List<RetrievalHit> search(String query, List<String> scopePaths, int limit, String agentCode) {
        return search(query, scopePaths, limit, agentCode, null, RetrievalConstraints.empty()).hits();
    }

    public List<RetrievalHit> search(
            String query,
            List<String> scopePaths,
            int limit,
            String agentCode,
            String documentId
    ) {
        return search(query, scopePaths, limit, agentCode, documentId, RetrievalConstraints.empty()).hits();
    }

    public RetrievalSearchResult search(
            String query,
            List<String> scopePaths,
            int limit,
            String agentCode,
            String documentId,
            RetrievalConstraints constraints
    ) {
        String searchQuery = queryRewriteService.rewrite(query);
        int candidateLimit = Math.max(limit, limit * ragProperties.getRetrieval().getCandidateMultiplier());
        RetrievalConstraints effectiveConstraints = constraints == null ? RetrievalConstraints.empty() : constraints;
        List<String> scopes = effectiveConstraints.effectiveScopePaths();
        if (scopes.isEmpty() && scopePaths != null) {
            scopes = scopePaths;
        }
        final List<String> searchScopes = scopes;
        if (documentId != null && !documentId.isBlank()) {
            List<RetrievalHit> documentHits = toHits(
                    documentChunkRepository.searchFullTextForDocument(searchQuery, documentId, candidateLimit));
            List<RetrievalHit> finalized = finalizeWithArticleRetry(
                    documentHits,
                    agentCode,
                    limit,
                    () -> toHits(documentChunkRepository.searchFullTextForDocument(
                            queryRewriteService.rewriteForArticleRetrieval(query),
                            documentId,
                            candidateLimit * 2
                    ))
            );
            return new RetrievalSearchResult(finalized, documentHits.size(), 0, documentHits.size(), 0, finalized.size());
        }

        ElasticsearchRetriever elasticsearchRetriever = elasticsearchRetrieverProvider.getIfAvailable();
        boolean esEnabled = elasticsearchRetriever != null && elasticsearchRetriever.isEnabled();

        List<RetrievalHit> fulltextHits;
        if (esEnabled) {
            EsSearchResult bm25 = elasticsearchRetriever.searchBm25Result(searchQuery, searchScopes, candidateLimit);
            if (bm25.failed()) {
                log.warn("Falling back to MySQL FULLTEXT after Elasticsearch BM25 failure");
                fulltextHits = toHits(documentChunkRepository.searchFullText(
                        searchQuery, searchScopes, searchScopes.size(), candidateLimit));
            } else {
                fulltextHits = bm25.hits();
            }
        } else {
            fulltextHits = toHits(documentChunkRepository.searchFullText(
                    searchQuery, searchScopes, searchScopes.size(), candidateLimit));
        }
        int fulltextCount = fulltextHits.size();

        if (!esEnabled || !embeddingService.isEnabled()) {
            fulltextHits = channelThresholdFilter.filterFullText(fulltextHits);
            fulltextHits = applyDocumentFilter(fulltextHits, effectiveConstraints);
            List<RetrievalHit> finalized = finalizeWithArticleRetry(
                    fulltextHits,
                    agentCode,
                    limit,
                    () -> fetchFullTextCandidates(
                            queryRewriteService.rewriteForArticleRetrieval(query),
                            searchScopes,
                            candidateLimit * 2,
                            effectiveConstraints,
                            null
                    )
            );
            return new RetrievalSearchResult(
                    finalized,
                    fulltextCount,
                    0,
                    fulltextHits.size(),
                    0,
                    finalized.size()
            );
        }

        List<RetrievalHit> vectorHits = embeddingService.embed(searchQuery)
                .map(vector -> {
                    EsSearchResult knn = elasticsearchRetriever.searchKnnResult(vector, searchScopes, candidateLimit);
                    if (knn.failed()) {
                        log.warn("Falling back to empty vector channel after Elasticsearch kNN failure");
                        return List.<RetrievalHit>of();
                    }
                    return knn.hits();
                })
                .orElse(List.of());
        int vectorCount = vectorHits.size();

        fulltextHits = channelThresholdFilter.filterFullText(fulltextHits);
        vectorHits = channelThresholdFilter.filterVector(vectorHits);
        fulltextHits = applyDocumentFilter(fulltextHits, effectiveConstraints);
        vectorHits = applyDocumentFilter(vectorHits, effectiveConstraints);

        if (vectorHits.isEmpty()) {
            List<RetrievalHit> finalized = finalizeWithArticleRetry(
                    fulltextHits,
                    agentCode,
                    limit,
                    () -> fetchFullTextCandidates(
                            queryRewriteService.rewriteForArticleRetrieval(query),
                            searchScopes,
                            candidateLimit * 2,
                            effectiveConstraints,
                            elasticsearchRetriever
                    )
            );
            return new RetrievalSearchResult(
                    finalized,
                    fulltextCount,
                    vectorCount,
                    fulltextHits.size(),
                    0,
                    finalized.size()
            );
        }
        List<RetrievalHit> fused = RrfFusion.fuse(List.of(fulltextHits, vectorHits), RrfFusion.DEFAULT_K, candidateLimit);
        List<RetrievalHit> finalized = finalizeWithArticleRetry(
                fused,
                agentCode,
                limit,
                () -> {
                    String articleQuery = queryRewriteService.rewriteForArticleRetrieval(query);
                    List<RetrievalHit> retryFulltext = fetchFullTextCandidates(
                            articleQuery,
                            searchScopes,
                            candidateLimit * 2,
                            effectiveConstraints,
                            elasticsearchRetriever
                    );
                    List<RetrievalHit> retryVector = embeddingService.embed(articleQuery)
                            .map(vector -> elasticsearchRetriever.searchKnn(vector, searchScopes, candidateLimit * 2))
                            .orElse(List.of());
                    retryVector = channelThresholdFilter.filterVector(retryVector);
                    retryVector = applyDocumentFilter(retryVector, effectiveConstraints);
                    if (retryVector.isEmpty()) {
                        return retryFulltext;
                    }
                    return RrfFusion.fuse(List.of(retryFulltext, retryVector), RrfFusion.DEFAULT_K, candidateLimit * 2);
                }
        );
        return new RetrievalSearchResult(
                finalized,
                fulltextCount,
                vectorCount,
                fulltextHits.size(),
                vectorHits.size(),
                finalized.size()
        );
    }

    private List<RetrievalHit> fetchFullTextCandidates(
            String searchQuery,
            List<String> searchScopes,
            int candidateLimit,
            RetrievalConstraints constraints,
            ElasticsearchRetriever elasticsearchRetriever
    ) {
        List<RetrievalHit> hits;
        if (elasticsearchRetriever != null && elasticsearchRetriever.isEnabled()) {
            EsSearchResult bm25 = elasticsearchRetriever.searchBm25Result(searchQuery, searchScopes, candidateLimit);
            if (bm25.failed()) {
                log.warn("Falling back to MySQL FULLTEXT after Elasticsearch BM25 failure");
                hits = toHits(documentChunkRepository.searchFullText(
                        searchQuery, searchScopes, searchScopes.size(), candidateLimit));
            } else {
                hits = bm25.hits();
            }
        } else {
            hits = toHits(documentChunkRepository.searchFullText(
                    searchQuery, searchScopes, searchScopes.size(), candidateLimit));
        }
        hits = channelThresholdFilter.filterFullText(hits);
        return applyDocumentFilter(hits, constraints);
    }

    private List<RetrievalHit> finalizeWithArticleRetry(
            List<RetrievalHit> candidates,
            String agentCode,
            int limit,
            java.util.function.Supplier<List<RetrievalHit>> retrySupplier
    ) {
        List<RetrievalHit> finalized = finalizeHits(candidates, agentCode, limit);
        if (!finalized.isEmpty() || candidates.isEmpty()) {
            return finalized;
        }
        List<RetrievalHit> retryCandidates = retrySupplier.get();
        return finalizeHits(retryCandidates, agentCode, limit);
    }

    private List<RetrievalHit> applyDocumentFilter(List<RetrievalHit> hits, RetrievalConstraints constraints) {
        if (!constraints.hasDocumentFilter()) {
            return hits;
        }
        List<String> allowed = constraints.documentIds();
        return hits.stream().filter(hit -> allowed.contains(hit.documentId())).toList();
    }

    private List<RetrievalHit> finalizeHits(List<RetrievalHit> hits, String agentCode, int limit) {
        List<RetrievalHit> substantive = retrievalSubstantiveFilter.filter(hits);
        List<RetrievalHit> reranked = retrievalReranker.rerank(substantive, agentCode);
        return retrievalDiversifier.diversify(
                reranked,
                limit,
                ragProperties.getRetrieval().getMaxChunksPerDocument()
        );
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
}
