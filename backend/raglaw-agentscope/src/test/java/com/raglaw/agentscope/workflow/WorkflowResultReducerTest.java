package com.raglaw.agentscope.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import com.raglaw.memory.casefile.CaseScope;
import com.raglaw.memory.service.CaseMemorySnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowResultReducerTest {
    @Test
    void conflictingEmploymentDatesRemainSeparate() {
        var snapshot = new CaseMemorySnapshot(new CaseScope("tenant", "user", "case"), 18, Instant.now());
        var first = result("node-1", "employment.startDate", "2020-01-01", 18);
        var second = result("node-2", "employment.startDate", "2021-01-01", 18);
        WorkflowReduction reduction = new WorkflowResultReducer().reduce(List.of(first, second), snapshot);
        assertThat(reduction.conflicts()).hasSize(1);
        assertThat(reduction.conflicts().get(0).slot()).isEqualTo("employment.startDate");
        assertThat(reduction.memoryCandidates()).isEmpty();
    }

    @Test
    void identicalFactsAreReducedToOneCandidate() {
        var snapshot = new CaseMemorySnapshot(new CaseScope("tenant", "user", "case"), 18, Instant.now());
        WorkflowReduction reduction = new WorkflowResultReducer().reduce(List.of(
                result("node-1", "salary", "15000", 18), result("node-2", "salary", "15000", 18)), snapshot);
        assertThat(reduction.conflicts()).isEmpty();
        assertThat(reduction.memoryCandidates()).hasSize(1);
    }

    private static NodeExecutionResult result(String id, String slot, String value, long snapshot) {
        return new NodeExecutionResult(id, id + "-key", "SUCCEEDED", Map.of("facts", List.of(Map.of("slot", slot, "value", value, "sourceId", id))), List.of(), snapshot);
    }
}
