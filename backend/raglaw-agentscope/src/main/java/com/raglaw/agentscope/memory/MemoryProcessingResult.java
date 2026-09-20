package com.raglaw.agentscope.memory;

import com.raglaw.memory.service.MemoryCommandResult;
import java.util.List;

public record MemoryProcessingResult(List<MemoryCommandResult> results, boolean synchronous, boolean skipped) {
    public MemoryProcessingResult {
        results = results == null ? List.of() : List.copyOf(results);
    }

    public static MemoryProcessingResult skippedResult() {
        return new MemoryProcessingResult(List.of(), true, true);
    }
}
