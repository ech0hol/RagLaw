package com.raglaw.memory.eval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MemoryBenchmarkTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void frozenBenchmarkContainsRequiredScenarioMix() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/memory-benchmark-v1.json")) {
            assertThat(input).isNotNull();
            JsonNode root = mapper.readTree(input);
            assertThat(root.path("benchmarkVersion").asText()).isEqualTo("memory-benchmark-v1");
            JsonNode cases = root.path("cases");
            assertThat(cases).hasSize(100);
            Map<String, Integer> actual = new HashMap<>();
            cases.forEach(item -> actual.merge(item.path("category").asText(), 1, Integer::sum));
            root.path("categories").fields().forEachRemaining(entry ->
                    assertThat(actual.getOrDefault(entry.getKey(), 0)).isEqualTo(entry.getValue().asInt()));
            assertThat(cases.findValuesAsText("id")).doesNotHaveDuplicates();
            assertThat(cases.findValuesAsText("protected")).isNotEmpty();
        }
    }
}
