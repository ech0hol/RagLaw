package com.raglaw.memory.service;

import java.util.List;

public record AssembledContext(
        String dataBlock,
        List<String> memoryIds,
        List<String> sourceIds,
        int characters,
        boolean truncated
) {
    public AssembledContext {
        dataBlock = dataBlock == null ? "" : dataBlock;
        memoryIds = memoryIds == null ? List.of() : List.copyOf(memoryIds);
        sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
    }
}
