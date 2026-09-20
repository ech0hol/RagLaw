package com.raglaw.memory.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContextBenchmarkTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void frozenContextDatasetComparesAllAssemblyStrategies() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/context-benchmark-v1.json")) {
            assertThat(input).as("context benchmark resource").isNotNull();
            JsonNode root = mapper.readTree(input);
            assertThat(root.path("benchmarkVersion").asText()).isEqualTo("context-benchmark-v1");
            JsonNode scenarios = root.path("scenarios");
            assertThat(scenarios).hasSize(100);
            Map<String, Integer> strategies = new HashMap<>();
            scenarios.forEach(scenario -> {
                strategies.merge(scenario.path("strategy").asText(), 1, Integer::sum);
                assertThat(scenario.path("criticalFactRetention").asDouble()).isEqualTo(1.0);
                assertThat(scenario.path("evidencePointerRetention").asDouble()).isEqualTo(1.0);
                assertThat(scenario.path("inputBudgetUtilization").asDouble()).isLessThanOrEqualTo(1.0);
                assertThat(scenario.path("crossCaseLeakage").asInt()).isZero();
                assertThat(scenario.path("unauthorizedHistoryExposure").asInt()).isZero();
            });
            assertThat(strategies).containsEntry("legacy-full-history", 25)
                    .containsEntry("recent-window", 25)
                    .containsEntry("summary-plus-tail", 25)
                    .containsEntry("layered-context", 25);
        }
    }
}
