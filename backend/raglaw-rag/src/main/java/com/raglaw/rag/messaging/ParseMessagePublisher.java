package com.raglaw.rag.messaging;

import com.raglaw.rag.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class ParseMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(ParseMessagePublisher.class);

    private final RagProperties ragProperties;
    private final ObjectProvider<RabbitTemplate> rabbitTemplate;

    public ParseMessagePublisher(RagProperties ragProperties, ObjectProvider<RabbitTemplate> rabbitTemplate) {
        this.ragProperties = ragProperties;
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishParseJob(String documentId) {
        if (!ragProperties.getRabbit().isEnabled()) {
            log.debug("RabbitMQ disabled, skip parse publish for document {}", documentId);
            return;
        }
        RabbitTemplate template = rabbitTemplate.getIfAvailable();
        if (template == null) {
            log.debug("RabbitTemplate unavailable, skip parse publish for document {}", documentId);
            return;
        }
        template.convertAndSend(ragProperties.getRabbit().getParseQueue(), documentId);
        log.info("Published parse job for document {}", documentId);
    }
}
