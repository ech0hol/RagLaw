package com.raglaw.rag.messaging;

import com.raglaw.rag.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class IndexMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(IndexMessagePublisher.class);

    private final RagProperties ragProperties;
    private final ObjectProvider<RabbitTemplate> rabbitTemplate;

    public IndexMessagePublisher(RagProperties ragProperties, ObjectProvider<RabbitTemplate> rabbitTemplate) {
        this.ragProperties = ragProperties;
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishIndexJob(String documentId) {
        if (!ragProperties.getRabbit().isEnabled()) {
            log.debug("RabbitMQ disabled, skip index publish for document {}", documentId);
            return;
        }
        RabbitTemplate template = rabbitTemplate.getIfAvailable();
        if (template == null) {
            log.debug("RabbitTemplate unavailable, skip index publish for document {}", documentId);
            return;
        }
        template.convertAndSend(ragProperties.getRabbit().getIndexQueue(), documentId);
        log.info("Published index job for document {}", documentId);
    }
}
