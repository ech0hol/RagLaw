package com.raglaw.rag.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.common.util.Ids;
import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.domain.IndexOutboxEntity;
import com.raglaw.rag.domain.IndexOutboxOperation;
import com.raglaw.rag.domain.IndexOutboxStatus;
import com.raglaw.rag.domain.IngestStage;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.repository.IndexOutboxRepository;
import com.raglaw.rag.search.ElasticsearchIndexService;
import com.raglaw.rag.search.ElasticsearchRetriever;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IndexOutboxService {

    private static final Logger log = LoggerFactory.getLogger(IndexOutboxService.class);

    private final IndexOutboxRepository indexOutboxRepository;
    private final DocumentRepository documentRepository;
    private final ObjectProvider<ElasticsearchRetriever> elasticsearchRetrieverProvider;
    private final RagProperties ragProperties;
    private final ObjectMapper objectMapper;

    public IndexOutboxService(
            IndexOutboxRepository indexOutboxRepository,
            DocumentRepository documentRepository,
            ObjectProvider<ElasticsearchRetriever> elasticsearchRetrieverProvider,
            RagProperties ragProperties,
            ObjectMapper objectMapper
    ) {
        this.indexOutboxRepository = indexOutboxRepository;
        this.documentRepository = documentRepository;
        this.elasticsearchRetrieverProvider = elasticsearchRetrieverProvider;
        this.ragProperties = ragProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void enqueueDeleteDocument(String documentId, long indexVersion) {
        indexOutboxRepository.save(new IndexOutboxEntity(
                Ids.newId(),
                documentId,
                null,
                IndexOutboxOperation.DELETE_DOC,
                null,
                indexVersion
        ));
    }

    /**
     * @deprecated UPSERT payloads are written directly to Elasticsearch to avoid large embedding
     *             JSON in MySQL outbox (sort buffer overflow). Outbox is DELETE-only in production.
     */
    @Deprecated(forRemoval = false)
    @Transactional
    public void enqueueBulkUpsert(
            String documentId,
            String docType,
            List<ElasticsearchIndexService.ChunkIndexEntry> chunks,
            long indexVersion
    ) {
        try {
            String payload = objectMapper.writeValueAsString(new BulkUpsertPayload(docType, chunks));
            indexOutboxRepository.save(new IndexOutboxEntity(
                    Ids.newId(),
                    documentId,
                    null,
                    IndexOutboxOperation.UPSERT,
                    payload,
                    indexVersion
            ));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize index outbox payload", ex);
        }
    }

    @Transactional
    public void flushPendingForDocument(String documentId) {
        ElasticsearchRetriever elasticsearchRetriever = elasticsearchRetrieverProvider.getIfAvailable();
        if (elasticsearchRetriever == null || !elasticsearchRetriever.isEnabled()) {
            return;
        }
        List<IndexOutboxEntity> pending = indexOutboxRepository.findByDocumentIdAndStatusOrderByCreatedAtAsc(
                documentId,
                IndexOutboxStatus.PENDING
        );
        processEntries(elasticsearchRetriever, pending);
    }

    @Transactional
    public int pollPendingBatch() {
        if (!ragProperties.getOutbox().isEnabled()) {
            return 0;
        }
        ElasticsearchRetriever elasticsearchRetriever = elasticsearchRetrieverProvider.getIfAvailable();
        if (elasticsearchRetriever == null || !elasticsearchRetriever.isEnabled()) {
            return 0;
        }

        int batchSize = ragProperties.getOutbox().getBatchSize();
        List<IndexOutboxEntity> pending = indexOutboxRepository.findByStatusOrderByCreatedAtAsc(
                IndexOutboxStatus.PENDING,
                PageRequest.of(0, batchSize)
        );
        if (pending.isEmpty()) {
            retryFailedEntries(elasticsearchRetriever);
            return 0;
        }

        Set<String> documentIds = new LinkedHashSet<>();
        for (IndexOutboxEntity entry : pending) {
            documentIds.add(entry.getDocumentId());
        }

        int processed = 0;
        for (String documentId : documentIds) {
            List<IndexOutboxEntity> documentEntries = indexOutboxRepository.findByDocumentIdAndStatusOrderByCreatedAtAsc(
                    documentId,
                    IndexOutboxStatus.PENDING
            );
            processed += processEntries(elasticsearchRetriever, documentEntries);
        }
        retryFailedEntries(elasticsearchRetriever);
        return processed;
    }

    private void retryFailedEntries(ElasticsearchRetriever elasticsearchRetriever) {
        Instant retryBefore = Instant.now().minusMillis(ragProperties.getOutbox().getFailedRetryDelayMs());
        List<IndexOutboxEntity> failed = indexOutboxRepository.findRetryableFailed(
                IndexOutboxStatus.FAILED,
                retryBefore,
                PageRequest.of(0, ragProperties.getOutbox().getBatchSize())
        );
        List<IndexOutboxEntity> resetEntries = new ArrayList<>();
        for (IndexOutboxEntity entry : failed) {
            if (entry.getAttemptCount() < ragProperties.getOutbox().getMaxAttempts()) {
                entry.resetForRetry();
                indexOutboxRepository.save(entry);
                resetEntries.add(entry);
            }
        }
        if (!resetEntries.isEmpty()) {
            Set<String> documentIds = new LinkedHashSet<>();
            for (IndexOutboxEntity entry : resetEntries) {
                documentIds.add(entry.getDocumentId());
            }
            for (String documentId : documentIds) {
                List<IndexOutboxEntity> documentEntries = indexOutboxRepository.findByDocumentIdAndStatusOrderByCreatedAtAsc(
                        documentId,
                        IndexOutboxStatus.PENDING
                );
                processEntries(elasticsearchRetriever, documentEntries);
            }
        }
    }

    private int processEntries(ElasticsearchRetriever elasticsearchRetriever, List<IndexOutboxEntity> entries) {
        int processed = 0;
        for (IndexOutboxEntity entry : entries) {
            try {
                if (shouldSkipStaleEntry(elasticsearchRetriever, entry)) {
                    entry.markDone();
                    indexOutboxRepository.save(entry);
                    processed++;
                    continue;
                }
                applyEntry(elasticsearchRetriever, entry);
                entry.markDone();
                indexOutboxRepository.save(entry);
                processed++;
            } catch (Exception ex) {
                log.warn("Index outbox flush failed for {}: {}", entry.getId(), ex.getMessage());
                boolean permanentlyFailed = entry.recordAttemptFailure(
                        ex.getMessage(),
                        ragProperties.getOutbox().getMaxAttempts()
                );
                indexOutboxRepository.save(entry);
                if (permanentlyFailed) {
                    markDocumentIngestFailed(entry.getDocumentId(), ex.getMessage());
                }
            }
        }
        return processed;
    }

    private boolean shouldSkipStaleEntry(ElasticsearchRetriever elasticsearchRetriever, IndexOutboxEntity entry)
            throws IOException {
        long storedVersion = elasticsearchRetriever.getDocumentIndexVersion(entry.getDocumentId());
        return entry.getIndexVersion() < storedVersion;
    }

    private void markDocumentIngestFailed(String documentId, String errorMessage) {
        documentRepository.findById(documentId).ifPresent(document -> {
            document.setIngestStage(IngestStage.FAILED);
            document.setIngestError(errorMessage);
            documentRepository.save(document);
        });
    }

    void applyEntry(ElasticsearchRetriever elasticsearchRetriever, IndexOutboxEntity entry) throws IOException {
        if (entry.getOperation() == IndexOutboxOperation.DELETE_DOC) {
            elasticsearchRetriever.deleteByDocumentIdOrThrow(entry.getDocumentId());
            return;
        }
        BulkUpsertPayload payload = readPayload(entry.getPayloadJson());
        elasticsearchRetriever.deleteByDocumentIdOrThrow(entry.getDocumentId());
        elasticsearchRetriever.bulkIndexDocumentOrThrow(
                entry.getDocumentId(),
                payload.docType(),
                payload.chunks(),
                entry.getIndexVersion()
        );
    }

    private BulkUpsertPayload readPayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return new BulkUpsertPayload("", List.of());
        }
        try {
            return objectMapper.readValue(payloadJson, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse index outbox payload", ex);
        }
    }

    private record BulkUpsertPayload(String docType, List<ElasticsearchIndexService.ChunkIndexEntry> chunks) {
    }
}
