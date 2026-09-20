package com.raglaw.agentscope.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class ExpertResolutionBenchmarkTest {
    @Test
    void frozenDatasetContainsOneHundredLabeledCases() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/expert-resolution-benchmark-v1.json")) {
            JsonNode root = new ObjectMapper().readTree(input);
            assertThat(root.path("benchmarkVersion").asText()).isEqualTo("expert-resolution-benchmark-v1");
            assertThat(root.path("cases")).hasSize(100);
            root.path("cases").forEach(item -> {
                assertThat(item.path("taskType").asText()).isNotBlank();
                assertThat(item.path("domain").asText()).isNotBlank();
                assertThat(item.path("riskLevel").asText()).isNotBlank();
                assertThat(item.path("safetyRationale").asText()).isNotBlank();
            });
        }
    }
}
