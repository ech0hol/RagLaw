package com.raglaw.rag.retrieval;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class RecallBenchmarkSpecTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void recallBenchmarkHasAtLeastTenQueries() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/recall-benchmark.json")) {
            JsonNode root = objectMapper.readTree(input);
            JsonNode queries = root.path("queries");
            assertTrue(queries.isArray());
            assertTrue(queries.size() >= 10, "benchmark should contain at least 10 queries");

            for (JsonNode query : queries) {
                assertFalse(query.path("text").asText("").isBlank(), "query text required");
                assertTrue(query.path("minHitCount").asInt(0) >= 1, "minHitCount must be >= 1");
            }
        }
    }
}
