package com.raglaw.memory.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;

class ContextCompactionServiceTest {
    @Test
    void compactsInDeterministicOrderBeforeCallingModel() {
        ContextSummarizer summarizer = mock(ContextSummarizer.class);
        when(summarizer.summarize(org.mockito.ArgumentMatchers.anyList())).thenReturn(
                new StructuredConversationSummary("task", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
        var service = new ContextCompactionService(Optional.of(summarizer));
        var result = service.compact(new CompactionRequest(List.of(
                new ContextItem("p0", ContextSectionType.CURRENT_TASK, ContextPriority.P0_REQUIRED, "task", 20, false, List.of()),
                new ContextItem("p3", ContextSectionType.TOOL_ARTIFACT, ContextPriority.P3_OPTIONAL, "noise", 20, true, List.of()),
                new ContextItem("p2", ContextSectionType.RECENT_CONVERSATION, ContextPriority.P2_COMPRESSIBLE, "old", 20, true, List.of())), 30));
        assertThat(result.appliedStages()).containsExactly("DEDUPLICATE", "DROP_P3", "OFFLOAD_LARGE_ARTIFACT", "SUMMARIZE_OLD_TURNS");
    }
}
