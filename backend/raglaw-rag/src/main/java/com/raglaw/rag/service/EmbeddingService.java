package com.raglaw.rag.service;

import com.raglaw.rag.config.RagProperties;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final RagProperties ragProperties;
    private final RestClient restClient;

    public EmbeddingService(RagProperties ragProperties) {
        this.ragProperties = ragProperties;
        this.restClient = RestClient.builder()
                .baseUrl("https://dashscope.aliyuncs.com")
                .build();
    }

    public boolean isEnabled() {
        RagProperties.Embedding embedding = ragProperties.getEmbedding();
        return embedding.isEnabled()
                && embedding.getApiKey() != null
                && !embedding.getApiKey().isBlank();
    }

    public Optional<float[]> embed(String text) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        try {
            EmbeddingResponse response = restClient.post()
                    .uri("/api/v1/services/embeddings/text-embedding/text-embedding")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + ragProperties.getEmbedding().getApiKey())
                    .body(new EmbeddingRequest(ragProperties.getEmbedding().getModel(), text))
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

    private record EmbeddingRequest(String model, String input) {
    }

    private record EmbeddingResponse(Output output) {
    }

    private record Output(List<EmbeddingItem> embeddings) {
    }

    private record EmbeddingItem(List<Double> embedding) {
    }
}
