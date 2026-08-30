package com.raglaw.rag.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorProperty;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.raglaw.rag.config.RagProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBean(ElasticsearchClient.class)
public class ElasticsearchIndexService {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchIndexService.class);
    private static final int VECTOR_DIMENSIONS = 1024;

    private final ElasticsearchClient client;
    private final RagProperties ragProperties;
    private final String indexName;

    public ElasticsearchIndexService(ElasticsearchClient client, RagProperties ragProperties) {
        this.client = client;
        this.ragProperties = ragProperties;
        this.indexName = ragProperties.getElasticsearch().getIndexName();
    }

    @PostConstruct
    public void init() {
        try {
            ensureIndex();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to initialize Elasticsearch index: " + indexName, ex);
        }
    }

    public void ensureIndex() throws IOException {
        boolean exists = client.indices().exists(e -> e.index(indexName)).value();
        if (exists) {
            return;
        }
        client.indices().create(c -> c.index(indexName).mappings(buildMapping()));
        log.info("Created Elasticsearch index {}", indexName);
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
    ) throws IOException {
        Map<String, Object> document = chunkDocument(
                chunkId,
                documentId,
                content,
                embedding,
                l1Path,
                l2Path,
                l3Path,
                docType
        );
        IndexResponse response = client.index(i -> i
                .index(indexName)
                .id(chunkId)
                .document(document));
        if (response.result() == null) {
            log.warn("Unexpected Elasticsearch upsert result for chunk {}", chunkId);
        }
    }

    public void deleteByDocumentId(String documentId) throws IOException {
        DeleteByQueryResponse response = client.deleteByQuery(d -> d
                .index(indexName)
                .query(q -> q.term(t -> t.field("document_id").value(documentId))));
        log.debug("Deleted {} Elasticsearch chunks for document {}", response.deleted(), documentId);
    }

    public void bulkIndexDocument(
            String documentId,
            String docType,
            List<ChunkIndexEntry> chunks,
            long indexVersion
    ) throws IOException {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        List<BulkOperation> operations = new ArrayList<>(chunks.size());
        for (ChunkIndexEntry chunk : chunks) {
            Map<String, Object> document = chunkDocument(
                    chunk.chunkId(),
                    documentId,
                    chunk.content(),
                    chunk.embedding(),
                    chunk.l1Path(),
                    chunk.l2Path(),
                    chunk.l3Path(),
                    docType,
                    indexVersion
            );
            operations.add(BulkOperation.of(op -> op.index(idx -> idx
                    .index(indexName)
                    .id(chunk.chunkId())
                    .document(document))));
        }
        client.bulk(BulkRequest.of(b -> b.index(indexName).operations(operations)));
    }

    public long getDocumentIndexVersion(String documentId) throws IOException {
        SearchResponse<Map> response = client.search(s -> s
                        .index(indexName)
                        .size(1)
                        .query(q -> q.term(t -> t.field("document_id").value(documentId)))
                        .source(src -> src.filter(f -> f.includes("index_version"))),
                Map.class);
        if (response.hits().hits().isEmpty()) {
            return 0L;
        }
        Map source = response.hits().hits().get(0).source();
        if (source == null || source.get("index_version") == null) {
            return 0L;
        }
        Object value = source.get("index_version");
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    public List<ChunkSearchHit> bm25Search(String query, List<String> scopes, int limit) throws IOException {
        SearchResponse<Map> response = client.search(s -> s
                        .index(indexName)
                        .size(limit)
                        .query(scopeAwareTextQuery(query, scopes)),
                Map.class);
        return toChunkHits(response);
    }

    public List<ChunkSearchHit> knnSearch(float[] queryVector, List<String> scopes, int limit) throws IOException {
        List<Float> vector = toFloatList(queryVector);
        int numCandidates = Math.max(limit * 2, 100);
        SearchResponse<Map> response = client.search(s -> s
                        .index(indexName)
                        .size(limit)
                        .knn(k -> k
                                .field("embedding")
                                .queryVector(vector)
                                .k(limit)
                                .numCandidates(numCandidates)
                                .filter(scopeFilter(scopes))),
                Map.class);
        return toChunkHits(response);
    }

    public List<DocumentSearchHit> searchDocumentsBm25(String query, List<String> scopes, int limit) throws IOException {
        SearchResponse<Map> response = client.search(s -> s
                        .index(indexName)
                        .size(limit)
                        .query(scopeAwareTextQuery(query, scopes))
                        .collapse(c -> c.field("document_id")),
                Map.class);
        List<DocumentSearchHit> hits = new ArrayList<>();
        for (Hit<Map> hit : response.hits().hits()) {
            Map source = hit.source();
            if (source == null) {
                continue;
            }
            Object documentId = source.get("document_id");
            if (documentId == null) {
                continue;
            }
            hits.add(new DocumentSearchHit(documentId.toString(), hit.score() == null ? 0.0 : hit.score()));
        }
        return hits;
    }

    public boolean isEnabled() {
        return ragProperties.getElasticsearch().isEnabled();
    }

    public String getIndexName() {
        return indexName;
    }

    private static TypeMapping buildMapping() {
        return TypeMapping.of(m -> m.properties(Map.of(
                "chunk_id", Property.of(p -> p.keyword(k -> k)),
                "document_id", Property.of(p -> p.keyword(k -> k)),
                "content", Property.of(p -> p.text(t -> t)),
                "embedding", Property.of(p -> p.denseVector(DenseVectorProperty.of(d -> d
                        .dims(VECTOR_DIMENSIONS)
                        .index(true)
                        .similarity("cosine")))),
                "l1_path", Property.of(p -> p.keyword(k -> k)),
                "l2_path", Property.of(p -> p.keyword(k -> k)),
                "l3_path", Property.of(p -> p.keyword(k -> k)),
                "doc_type", Property.of(p -> p.keyword(k -> k)),
                "index_version", Property.of(p -> p.long_(l -> l))
        )));
    }

    private static Map<String, Object> chunkDocument(
            String chunkId,
            String documentId,
            String content,
            float[] embedding,
            String l1Path,
            String l2Path,
            String l3Path,
            String docType,
            long indexVersion
    ) {
        Map<String, Object> document = new HashMap<>();
        document.put("chunk_id", chunkId);
        document.put("document_id", documentId);
        document.put("content", content);
        document.put("embedding", toFloatList(embedding));
        document.put("l1_path", l1Path);
        document.put("l2_path", l2Path);
        document.put("l3_path", l3Path);
        document.put("doc_type", docType);
        document.put("index_version", indexVersion);
        return document;
    }

    private static Map<String, Object> chunkDocument(
            String chunkId,
            String documentId,
            String content,
            float[] embedding,
            String l1Path,
            String l2Path,
            String l3Path,
            String docType
    ) {
        return chunkDocument(chunkId, documentId, content, embedding, l1Path, l2Path, l3Path, docType, 0L);
    }

    private Query scopeAwareTextQuery(String query, List<String> scopes) {
        return Query.of(q -> q.bool(b -> {
            BoolQuery.Builder builder = b.must(m -> m.match(ma -> ma.field("content").query(query)));
            Query filter = scopeFilter(scopes);
            if (filter != null) {
                builder.filter(filter);
            }
            return builder;
        }));
    }

    private Query scopeFilter(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return null;
        }
        List<FieldValue> values = scopes.stream().map(FieldValue::of).collect(Collectors.toList());
        return Query.of(q -> q.bool(b -> b
                .should(s -> s.terms(t -> t.field("l2_path").terms(v -> v.value(values))))
                .should(s -> s.terms(t -> t.field("l3_path").terms(v -> v.value(values))))
                .minimumShouldMatch("1")));
    }

    private List<ChunkSearchHit> toChunkHits(SearchResponse<Map> response) {
        List<ChunkSearchHit> hits = new ArrayList<>();
        for (Hit<Map> hit : response.hits().hits()) {
            Map source = hit.source();
            if (source == null) {
                continue;
            }
            hits.add(new ChunkSearchHit(
                    stringValue(source.get("chunk_id")),
                    stringValue(source.get("document_id")),
                    stringValue(source.get("content")),
                    stringValue(source.get("l1_path")),
                    stringValue(source.get("l2_path")),
                    stringValue(source.get("l3_path")),
                    hit.score() == null ? 0.0 : hit.score()
            ));
        }
        return hits;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static List<Float> toFloatList(float[] embedding) {
        List<Float> vector = new ArrayList<>(embedding.length);
        for (float value : embedding) {
            vector.add(value);
        }
        return vector;
    }

    public record ChunkIndexEntry(
            String chunkId,
            String content,
            float[] embedding,
            String l1Path,
            String l2Path,
            String l3Path
    ) {
    }

    public record DocumentSearchHit(String documentId, double score) {
    }
}
