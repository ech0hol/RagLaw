package com.raglaw.agentscope.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkflowStateBenchmarkTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void frozenWorkflowDatasetCoversSafetyAndQualityGates() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/workflow-state-benchmark-v1.json")) {
            assertThat(input).as("workflow benchmark resource").isNotNull();
            JsonNode root = mapper.readTree(input);
            assertThat(root.path("benchmarkVersion").asText()).isEqualTo("workflow-state-benchmark-v1");
            JsonNode thresholds = root.path("thresholds");
            assertThat(thresholds.isObject()).isTrue();
            assertThat(thresholds.path("completionRate").isNumber()).isTrue();
            assertThat(thresholds.path("expertTop1Accuracy").isNumber()).isTrue();
            assertThat(thresholds.path("noCandidateAccuracy").isNumber()).isTrue();
            assertThat(thresholds.path("p95ContextBudgetUtilization").isNumber()).isTrue();
            JsonNode scenarios = root.path("scenarios");
            assertThat(scenarios).hasSize(60);
            Set<String> ids = new HashSet<>();
            scenarios.forEach(scenario -> {
                assertThat(ids.add(scenario.path("id").asText())).isTrue();
                assertThat(scenario.path("id").asText()).isNotBlank();
                assertThat(scenario.path("expectedCompletion").isBoolean()).isTrue();
                assertThat(scenario.path("expertTop1Correct").isBoolean()).isTrue();
                assertThat(scenario.path("noCandidateCorrect").isBoolean()).isTrue();
                assertThat(scenario.path("contextBudgetUtilization").isNumber()).isTrue();
                assertThat(scenario.path("expectedCompletion").asBoolean()).isTrue();
                assertThat(scenario.path("expertTop1Correct").asBoolean()).isTrue();
                assertThat(scenario.path("noCandidateCorrect").asBoolean()).isTrue();
                assertThat(scenario.path("contextBudgetUtilization").asDouble())
                        .isLessThanOrEqualTo(thresholds.path("p95ContextBudgetUtilization").asDouble());
                JsonNode safety = scenario.path("safety");
                assertThat(safety.isObject()).isTrue();
                assertThat(safety.path("crossCaseLeakage").asInt()).isZero();
                assertThat(safety.path("unauthorizedTools").asInt()).isZero();
                assertThat(safety.path("duplicateAcceptedResults").asInt()).isZero();
                assertThat(safety.path("snapshotInconsistency").asInt()).isZero();
                assertThat(safety.path("factStatusPromotion").asInt()).isZero();
                assertThat(safety.path("criticalFactLoss").asInt()).isZero();
                assertThat(safety.path("evidencePointerLoss").asInt()).isZero();
            });
        }
    }
}
