package com.raglaw.rag.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DashScopeChatClient implements LlmChatClient {

    private static final Logger log = LoggerFactory.getLogger(DashScopeChatClient.class);
    private static final URI CHAT_URI = URI.create(
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
    );

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final boolean mockEnabled;

    public DashScopeChatClient(
            ObjectMapper objectMapper,
            @Value("${raglaw.llm.dashscope-api-key:}") String apiKey,
            @Value("${raglaw.llm.mock:false}") boolean mockEnabled
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.mockEnabled = mockEnabled;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public String completeJson(String systemPrompt, String userMessage, String model) {
        if (mockEnabled) {
            return mockResponse(userMessage);
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置 DASHSCOPE_API_KEY，无法执行合同 LLM 审查");
        }
        String dashModel = normalizeModel(model);
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model", dashModel);
            body.put("stream", false);
            body.put("response_format", Map.of("type", "json_object"));
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

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() >= 400) {
                log.warn("DashScope chat failed: HTTP {} body={}", response.statusCode(), response.body());
                throw new IllegalStateException("DashScope 调用失败: HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("DashScope chat failed: {}", ex.getMessage());
            throw new IllegalStateException("DashScope 调用失败: " + ex.getMessage(), ex);
        }
    }

    private static String normalizeModel(String model) {
        if (model == null || model.isBlank()) {
            return "qwen-plus";
        }
        return model.startsWith("dashscope:") ? model.substring("dashscope:".length()) : model;
    }

    private String mockResponse(String userMessage) {
        String excerpt = "违约方应支付合同总价30%的违约金";
        if (userMessage != null) {
            int marker = userMessage.indexOf("chunkIndex=0");
            if (marker < 0) {
                marker = userMessage.indexOf("\"index\": 0");
            }
            if (marker >= 0) {
                int contentStart = userMessage.indexOf("content=", marker);
                if (contentStart >= 0) {
                    int lineEnd = userMessage.indexOf('\n', contentStart);
                    String line = lineEnd > contentStart
                            ? userMessage.substring(contentStart, lineEnd)
                            : userMessage.substring(contentStart);
                    int eq = line.indexOf('=');
                    if (eq >= 0 && eq + 1 < line.length()) {
                        String candidate = line.substring(eq + 1).trim();
                        if (candidate.length() > 10) {
                            excerpt = candidate.length() <= 80 ? candidate : candidate.substring(0, 80);
                        }
                    }
                }
            }
        }
        return """
                {
                  "risks": [
                    {
                      "chunkIndex": 0,
                      "severity": "HIGH",
                      "dimension": "违约责任",
                      "summary": "违约金比例可能过高（Mock）",
                      "excerpt": "%s",
                      "suggestion": "建议将违约金调整为合理比例，并明确计算基数与上限。",
                      "legalBasis": ["相关法律规定"]
                    }
                  ]
                }
                """.formatted(excerpt.replace("\"", "\\\""));
    }
}
