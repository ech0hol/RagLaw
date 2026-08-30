package com.raglaw.rag.service;

import com.raglaw.rag.config.RagProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final RagProperties ragProperties;
    private final RestClient restClient;
    private final boolean llmMock;

    public EmbeddingService(
            RagProperties ragProperties,
            @Value("${raglaw.llm.mock:false}") boolean llmMock
    ) {
        this.ragProperties = ragProperties;
        this.llmMock = llmMock;
        this.restClient = RestClient.builder()
                .baseUrl("https://dashscope.aliyuncs.com")
                .build();
    }

    public boolean isEnabled() {
        if (llmMock && ragProperties.getEmbedding().isEnabled()) {
            return true;
        }
        RagProperties.Embedding embedding = ragProperties.getEmbedding();
        return embedding.isEnabled()
                && embedding.getApiKey() != null
                && !embedding.getApiKey().isBlank();
    }

    public boolean isMockMode() {
        return llmMock && ragProperties.getEmbedding().isEnabled();
    }

    public Optional<float[]> embed(String text) {
        return embed(text, "query");
    }

    public Optional<float[]> embedDocument(String text) {
        return embed(text, "document");
    }

    private Optional<float[]> embed(String text, String textType) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        if (isMockMode()) {
            return Optional.of(mockEmbed(text));
        }
        try {
            EmbeddingResponse response = restClient.post()
                    .uri("/api/v1/services/embeddings/text-embedding/text-embedding")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + ragProperties.getEmbedding().getApiKey())
                    .body(new EmbeddingRequest(
                            ragProperties.getEmbedding().getModel(),
                            new EmbeddingInput(List.of(text)),
                            new EmbeddingParameters(ragProperties.getEmbedding().getDimensions(), textType)
                    ))
                    .retrieve()
                    .body(EmbeddingResponse.class);
            if (response == null || response.output() == null || response.output().embeddings().isEmpty()) {
                return Optional.empty();
            }
            List<Double> values = response.output().embeddings().get(0).embedding();
            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                vector[i] = values.get(i).floatValue();
            }
            return Optional.of(vector);
        } catch (Exception ex) {
            log.warn("Embedding request failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private float[] mockEmbed(String text) {
        int dim = ragProperties.getEmbedding().getDimensions();
        float[] vector = new float[dim];
        byte[] seed = (text == null ? "" : text).getBytes(StandardCharsets.UTF_8);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seed);
            for (int i = 0; i < dim; i++) {
                int b = hash[i % hash.length] & 0xff;
                vector[i] = (float) ((b / 255.0) * 2.0 - 1.0);
            }
        } catch (Exception ex) {
            for (int i = 0; i < dim; i++) {
                vector[i] = (float) Math.sin(text.hashCode() + i);
            }
        }
        double norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < dim; i++) {
                vector[i] = (float) (vector[i] / norm);
            }
        }
        return vector;
    }

    private record EmbeddingRequest(String model, EmbeddingInput input, EmbeddingParameters parameters) {
    }

    private record EmbeddingInput(List<String> texts) {
    }

    private record EmbeddingParameters(int dimension, String text_type) {
    }

    private record EmbeddingResponse(Output output) {
    }

    private record Output(List<EmbeddingItem> embeddings) {
    }

    private record EmbeddingItem(List<Double> embedding) {
    }
}
