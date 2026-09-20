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
            JsonNode thresholds = root.path("thresholds");
            assertThat(thresholds.isObject()).isTrue();
            assertThat(thresholds.path("criticalFactRetention").isNumber()).isTrue();
            assertThat(thresholds.path("evidencePointerRetention").isNumber()).isTrue();
            assertThat(thresholds.path("p95InputBudgetUtilization").isNumber()).isTrue();
            JsonNode scenarios = root.path("scenarios");
            assertThat(scenarios).hasSize(100);
            Map<String, Integer> strategies = new HashMap<>();
            scenarios.forEach(scenario -> {
                assertThat(scenario.path("id").asText()).isNotBlank();
                assertThat(scenario.path("strategy").asText()).isNotBlank();
                assertThat(scenario.path("criticalFactRetention").isNumber()).isTrue();
                assertThat(scenario.path("evidencePointerRetention").isNumber()).isTrue();
                assertThat(scenario.path("inputBudgetUtilization").isNumber()).isTrue();
                assertThat(scenario.path("crossCaseLeakage").isNumber()).isTrue();
                assertThat(scenario.path("unauthorizedHistoryExposure").isNumber()).isTrue();
                strategies.merge(scenario.path("strategy").asText(), 1, Integer::sum);
                assertThat(scenario.path("criticalFactRetention").asDouble())
                        .isGreaterThanOrEqualTo(thresholds.path("criticalFactRetention").asDouble());
                assertThat(scenario.path("evidencePointerRetention").asDouble())
                        .isGreaterThanOrEqualTo(thresholds.path("evidencePointerRetention").asDouble());
                assertThat(scenario.path("inputBudgetUtilization").asDouble())
                        .isLessThanOrEqualTo(thresholds.path("p95InputBudgetUtilization").asDouble());
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
