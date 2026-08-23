package com.raglaw.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.raglaw.rag.config.RagProperties;
import org.junit.jupiter.api.Test;

class EmbeddingServiceTest {

    @Test
    void mockModeProducesDeterministicNormalizedVector() {
        RagProperties properties = new RagProperties();
        properties.getEmbedding().setEnabled(true);
        properties.getEmbedding().setDimensions(16);
        EmbeddingService service = new EmbeddingService(properties, true);

        assertTrue(service.isEnabled());
        assertTrue(service.isMockMode());

        float[] first = service.embed("劳动合同").orElseThrow();
        float[] second = service.embed("劳动合同").orElseThrow();
        float[] other = service.embed("不同文本").orElseThrow();

        assertEquals(16, first.length);
        for (int i = 0; i < first.length; i++) {
            assertEquals(first[i], second[i], 0.0001f);
        }
        boolean differs = false;
        for (int i = 0; i < first.length; i++) {
            if (Math.abs(first[i] - other[i]) > 0.0001f) {
                differs = true;
                break;
            }
        }
        assertTrue(differs);
    }
}
