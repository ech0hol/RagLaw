package com.raglaw.rag.messaging;

import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.service.IngestPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "raglaw.rag.rabbit", name = "enabled", havingValue = "true")
public class ParseMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(ParseMessageConsumer.class);

    private final DocumentRepository documentRepository;
    private final IngestPipeline ingestPipeline;
    private final IndexMessagePublisher indexMessagePublisher;

    public ParseMessageConsumer(
            DocumentRepository documentRepository,
            IngestPipeline ingestPipeline,
            IndexMessagePublisher indexMessagePublisher
    ) {
        this.documentRepository = documentRepository;
        this.ingestPipeline = ingestPipeline;
        this.indexMessagePublisher = indexMessagePublisher;
    }

    @RabbitListener(queues = "${raglaw.rag.rabbit.parse-queue}")
    public void onParseJob(String documentId) {
        documentRepository.findById(documentId).ifPresentOrElse(document -> {
            try {
                log.info("Async parse started for document {}", documentId);
                ingestPipeline.parse(document);
                indexMessagePublisher.publishIndexJob(documentId);
                log.info("Async parse completed for document {}", documentId);
            } catch (Exception ex) {
                log.error("Async parse failed for document {}: {}", documentId, ex.getMessage());
                ingestPipeline.markFailed(document, ex);
                throw ex;
            }
        }, () -> log.warn("Parse job received for missing document {}", documentId));
    }
}
