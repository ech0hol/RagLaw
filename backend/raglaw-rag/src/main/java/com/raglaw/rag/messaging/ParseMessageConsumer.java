package com.raglaw.rag.messaging;

import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.service.IngestService;
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
    private final IngestService ingestService;

    public ParseMessageConsumer(DocumentRepository documentRepository, IngestService ingestService) {
        this.documentRepository = documentRepository;
        this.ingestService = ingestService;
    }

    @RabbitListener(queues = "${raglaw.rag.rabbit.parse-queue}")
    public void onParseJob(String documentId) {
        documentRepository.findById(documentId).ifPresentOrElse(document -> {
            try {
                log.info("Async ingest started for document {}", documentId);
                ingestService.ingest(document);
                log.info("Async ingest completed for document {}", documentId);
            } catch (Exception ex) {
                log.error("Async ingest failed for document {}: {}", documentId, ex.getMessage());
            }
        }, () -> log.warn("Parse job received for missing document {}", documentId));
    }
}
