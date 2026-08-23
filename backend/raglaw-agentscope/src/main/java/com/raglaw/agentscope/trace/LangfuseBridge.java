package com.raglaw.agentscope.trace;

import com.raglaw.agentscope.config.LangfuseProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@EnableConfigurationProperties(LangfuseProperties.class)
public class LangfuseBridge {

    private static final Logger log = LoggerFactory.getLogger(LangfuseBridge.class);

    private final LangfuseProperties properties;
    private final RestClient restClient;

    public LangfuseBridge(LangfuseProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(trimTrailingSlash(properties.getHost()))
                .defaultHeader("Authorization", "Basic " + basicAuth(properties))
                .build();
    }

    public Optional<String> startTrace(String traceId, String userId, String queryText, String agentCode) {
        if (!properties.isConfigured()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> body = Map.of(
                    "id", traceId,
                    "name", "agui-run",
                    "userId", userId == null ? "" : userId,
                    "input", queryText == null ? "" : queryText,
                    "metadata", Map.of("agentCode", agentCode == null ? "" : agentCode)
            );
            ingest("trace-create", body);
            return Optional.of(traceId);
        } catch (Exception e) {
            log.warn("Langfuse trace-create failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void completeTrace(String traceId, long latencyMs) {
        if (!properties.isConfigured() || traceId == null || traceId.isBlank()) {
            return;
        }
        try {
            ingest("trace-update", Map.of(
                    "id", traceId,
                    "metadata", Map.of("latencyMs", latencyMs)
            ));
        } catch (Exception e) {
            log.warn("Langfuse trace-update failed: {}", e.getMessage());
        }
    }

    public String traceUrl(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return null;
        }
        return trimTrailingSlash(properties.getHost()) + "/trace/" + traceId;
    }

    private void ingest(String type, Map<String, Object> body) {
        Map<String, Object> event = Map.of(
                "id", UUID.randomUUID().toString(),
                "type", type,
                "timestamp", Instant.now().toString(),
                "body", body
        );
        restClient.post()
                .uri("/api/public/ingestion")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("batch", List.of(event)))
                .retrieve()
                .toBodilessEntity();
    }

    private static String basicAuth(LangfuseProperties properties) {
        String raw = properties.getPublicKey() + ":" + properties.getSecretKey();
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String trimTrailingSlash(String host) {
        if (host == null || host.isBlank()) {
            return "http://localhost:3001";
        }
        return host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
    }
}
