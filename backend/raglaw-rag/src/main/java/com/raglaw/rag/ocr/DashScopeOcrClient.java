package com.raglaw.rag.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DashScopeOcrClient {

    private static final Logger log = LoggerFactory.getLogger(DashScopeOcrClient.class);
    private static final URI OCR_URI = URI.create(
            "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
    );

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final boolean mockEnabled;

    public DashScopeOcrClient(
            ObjectMapper objectMapper,
            @Value("${raglaw.rag.embedding.api-key:}") String apiKey,
            @Value("${raglaw.llm.mock:false}") boolean mockEnabled
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.mockEnabled = mockEnabled;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public Optional<String> extractTextFromPdf(byte[] pdfBytes) {
        if (mockEnabled) {
            return Optional.of("【OCR Mock】扫描件文本提取占位。请配置 DASHSCOPE_API_KEY 以启用真实 OCR。");
        }
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        try {
            String base64 = Base64.getEncoder().encodeToString(pdfBytes);
            Map<String, Object> body = Map.of(
                    "model", "qwen-vl-plus",
                    "messages", List.of(Map.of(
                            "role", "user",
                            "content", List.of(
                                    Map.of("type", "text", "text", "请提取该 PDF 合同中的全部文字，按原文顺序输出，不要总结。"),
                                    Map.of("type", "image_url", "image_url", Map.of(
                                            "url", "data:application/pdf;base64," + base64
                                    ))
                            )
                    ))
            );
            HttpRequest request = HttpRequest.newBuilder(OCR_URI)
                    .timeout(Duration.ofMinutes(2))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                log.warn("DashScope OCR failed: HTTP {}", response.statusCode());
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            return content.isBlank() ? Optional.empty() : Optional.of(content);
        } catch (Exception ex) {
            log.warn("DashScope OCR failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
