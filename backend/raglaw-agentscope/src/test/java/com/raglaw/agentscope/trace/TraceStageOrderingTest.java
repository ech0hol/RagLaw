package com.raglaw.agentscope.trace;

import static org.assertj.core.api.Assertions.assertThat;

import com.raglaw.agentscope.dto.TraceStageDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class TraceStageOrderingTest {

    @Test
    void sort_ordersKnownStagesBeforeUnknown() {
        List<TraceStageDto> sorted = TraceStageOrdering.sort(List.of(
                new TraceStageDto("3", "llm", null, 100L),
                new TraceStageDto("1", "contract_rag_context", null, 10L),
                new TraceStageDto("2", "rag_search", null, 50L),
                new TraceStageDto("4", "custom_stage", null, 5L)
        ));

        assertThat(sorted.stream().map(TraceStageDto::stage).toList())
                .containsExactly("contract_rag_context", "rag_search", "llm", "custom_stage");
    }
}
