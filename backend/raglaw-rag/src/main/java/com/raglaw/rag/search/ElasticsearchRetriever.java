package com.raglaw.rag.search;

import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.dto.RetrievalHit;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBean(ElasticsearchIndexService.class)
public class ElasticsearchRetriever {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchRetriever.class);

    private final ElasticsearchIndexService indexService;
    private final RagProperties ragProperties;

    public ElasticsearchRetriever(ElasticsearchIndexService indexService, RagProperties ragProperties) {
        this.indexService = indexService;
        this.ragProperties = ragProperties;
    }

    public boolean isEnabled() {
        return ragProperties.getElasticsearch().isEnabled();
    }

    public List<RetrievalHit> searchBm25(String query, List<String> scopes, int limit) {
        return searchBm25Result(query, scopes, limit).hits();
    }

    public EsSearchResult searchBm25Result(String query, List<String> scopes, int limit) {
        try {
            return EsSearchResult.success(toRetrievalHits(indexService.bm25Search(query, scopes, limit)));
        } catch (IOException ex) {
            log.warn("Elasticsearch BM25 search failed: {}", ex.getMessage());
            return EsSearchResult.failure(ex);
        }
    }

    public List<RetrievalHit> searchKnn(float[] queryVector, List<String> scopes, int limit) {
        return searchKnnResult(queryVector, scopes, limit).hits();
    }

    public EsSearchResult searchKnnResult(float[] queryVector, List<String> scopes, int limit) {
        try {
            return EsSearchResult.success(toRetrievalHits(indexService.knnSearch(queryVector, scopes, limit)));
        } catch (IOException ex) {
            log.warn("Elasticsearch kNN search failed: {}", ex.getMessage());
            return EsSearchResult.failure(ex);
        }
    }

    public List<ElasticsearchIndexService.DocumentSearchHit> searchDocumentsBm25(
            String query,
            List<String> scopes,
            int limit
    ) {
        try {
            return indexService.searchDocumentsBm25(query, scopes, limit);
        } catch (IOException ex) {
            log.warn("Elasticsearch document BM25 search failed: {}", ex.getMessage());
            return List.of();
        }
    }

    public void upsertChunk(
            String chunkId,
            String documentId,
            String content,
            float[] embedding,
            String l1Path,
            String l2Path,
            String l3Path,
            String docType
    ) {
        try {
            indexService.upsertChunk(
                    chunkId,
                    documentId,
                    content,
                    embedding,
                    l1Path,
                    l2Path,
                    l3Path,
                    docType
            );
        } catch (IOException ex) {
            log.warn("Elasticsearch chunk upsert failed: {}", ex.getMessage());
        }
    }

    public void deleteByDocumentId(String documentId) {
        try {
            deleteByDocumentIdOrThrow(documentId);
        } catch (IOException ex) {
            log.warn("Elasticsearch chunk delete failed: {}", ex.getMessage());
        }
    }

    public void deleteByDocumentIdOrThrow(String documentId) throws IOException {
        indexService.deleteByDocumentId(documentId);
    }

    public void bulkIndexDocument(
            String documentId,
            String docType,
            List<ElasticsearchIndexService.ChunkIndexEntry> chunks,
            long indexVersion
    ) {
        try {
            bulkIndexDocumentOrThrow(documentId, docType, chunks, indexVersion);
        } catch (IOException ex) {
            log.warn("Elasticsearch bulk index failed: {}", ex.getMessage());
        }
    }

    public void bulkIndexDocumentOrThrow(
            String documentId,
            String docType,
            List<ElasticsearchIndexService.ChunkIndexEntry> chunks,
            long indexVersion
    ) throws IOException {
        indexService.bulkIndexDocument(documentId, docType, chunks, indexVersion);
    }

    public long getDocumentIndexVersion(String documentId) throws IOException {
        return indexService.getDocumentIndexVersion(documentId);
    }

    public void bulkIndexDocument(String documentId, String docType, List<ElasticsearchIndexService.ChunkIndexEntry> chunks) {
        bulkIndexDocument(documentId, docType, chunks, 0L);
    }

    private List<RetrievalHit> toRetrievalHits(List<ChunkSearchHit> hits) {
        return hits.stream()
                .map(hit -> new RetrievalHit(
                        hit.chunkId(),
                        hit.documentId(),
                        hit.content(),
                        hit.l1Path(),
                        hit.l2Path(),
                        hit.l3Path(),
                        hit.score()
                ))
                .toList();
    }
}
