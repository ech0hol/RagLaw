package com.raglaw.agentscope.agui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DashScopeClient {

    private static final Logger log = LoggerFactory.getLogger(DashScopeClient.class);
    private static final URI CHAT_URI = URI.create(
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
    );

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DashScopeClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public LlmStreamResult streamChat(
            String apiKey,
            String model,
            String systemPrompt,
            String userMessage,
            Consumer<String> onDelta,
            Runnable cancellationCheck
    ) throws Exception {
        String dashModel = model.startsWith("dashscope:") ? model.substring("dashscope:".length()) : model;

        Map<String, Object> body = new HashMap<>();
        body.put("model", dashModel);
        body.put("stream", true);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt != null ? systemPrompt : ""),
                Map.of("role", "user", "content", userMessage)
        ));

        HttpRequest request = HttpRequest.newBuilder(CHAT_URI)
                .timeout(Duration.ofMinutes(2))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<java.io.InputStream> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofInputStream()
        );

        if (response.statusCode() != 200) {
            String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            throw new IllegalStateException("DashScope error " + response.statusCode() + ": " + errorBody);
        }

        StringBuilder fullText = new StringBuilder();
        Integer promptTokens = null;
        Integer completionTokens = null;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (cancellationCheck != null) {
                    cancellationCheck.run();
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String payload = line.substring(5).trim();
                if (payload.isEmpty() || "[DONE]".equals(payload)) {
                    continue;
                }
                JsonNode root = objectMapper.readTree(payload);
                JsonNode usage = root.path("usage");
                if (!usage.isMissingNode()) {
                    if (usage.has("prompt_tokens")) {
                        promptTokens = usage.get("prompt_tokens").asInt();
                    }
                    if (usage.has("completion_tokens")) {
                        completionTokens = usage.get("completion_tokens").asInt();
                    }
                }
                JsonNode delta = root.path("choices").path(0).path("delta").path("content");
                if (!delta.isMissingNode() && !delta.isNull()) {
                    String text = delta.asText();
                    if (!text.isEmpty()) {
                        fullText.append(text);
                        onDelta.accept(text);
                    }
                }
            }
        }

        return new LlmStreamResult(fullText.toString(), promptTokens, completionTokens);
    }

    public record LlmStreamResult(String text, Integer promptTokens, Integer completionTokens) {
    }
}
