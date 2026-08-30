package com.raglaw.rag.messaging;

import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.service.IndexOutboxService;
import com.raglaw.rag.service.IngestPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "raglaw.rag.rabbit", name = "enabled", havingValue = "true")
public class IndexMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(IndexMessageConsumer.class);

    private final DocumentRepository documentRepository;
    private final IngestPipeline ingestPipeline;
    private final IndexOutboxService indexOutboxService;

    public IndexMessageConsumer(
            DocumentRepository documentRepository,
            IngestPipeline ingestPipeline,
            IndexOutboxService indexOutboxService
    ) {
        this.documentRepository = documentRepository;
        this.ingestPipeline = ingestPipeline;
        this.indexOutboxService = indexOutboxService;
    }

    @RabbitListener(queues = "${raglaw.rag.rabbit.index-queue}")
    public void onIndexJob(String documentId) {
        documentRepository.findById(documentId).ifPresentOrElse(document -> {
            try {
                log.info("Async index started for document {}", documentId);
                ingestPipeline.index(document);
                indexOutboxService.flushPendingForDocument(documentId);
                log.info("Async index completed for document {}", documentId);
            } catch (Exception ex) {
                log.error("Async index failed for document {}: {}", documentId, ex.getMessage());
                ingestPipeline.markFailed(document, ex);
                throw ex;
            }
        }, () -> log.warn("Index job received for missing document {}", documentId));
    }
}
