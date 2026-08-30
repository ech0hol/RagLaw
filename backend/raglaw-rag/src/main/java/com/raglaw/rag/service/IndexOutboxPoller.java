package com.raglaw.rag.service;

import com.raglaw.rag.config.RagProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "raglaw.rag.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IndexOutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(IndexOutboxPoller.class);

    private final IndexOutboxService indexOutboxService;
    private final RagProperties ragProperties;

    public IndexOutboxPoller(IndexOutboxService indexOutboxService, RagProperties ragProperties) {
        this.indexOutboxService = indexOutboxService;
        this.ragProperties = ragProperties;
    }

    @Scheduled(fixedDelayString = "${raglaw.rag.outbox.poll-interval-ms:30000}")
    public void poll() {
        if (!ragProperties.getOutbox().isEnabled()) {
            return;
        }
        try {
            int processed = indexOutboxService.pollPendingBatch();
            if (processed > 0) {
                log.debug("Index outbox poller processed {} entries", processed);
            }
        } catch (Exception ex) {
            log.warn("Index outbox poller failed: {}", ex.getMessage());
        }
    }
}
