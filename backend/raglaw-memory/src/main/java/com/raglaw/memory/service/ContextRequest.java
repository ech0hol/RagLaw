package com.raglaw.memory.service;

import java.util.Set;

public record ContextRequest(
        CaseMemorySnapshot snapshot,
        String taskType,
        Set<String> requiredPredicates,
        int maxCharacters
) {
    public ContextRequest {
        if (snapshot == null) throw new IllegalArgumentException("snapshot");
        taskType = taskType == null || taskType.isBlank() ? "GENERAL_CONSULTATION" : taskType.trim();
        requiredPredicates = requiredPredicates == null ? Set.of() : Set.copyOf(requiredPredicates);
        if (maxCharacters <= 0) throw new IllegalArgumentException("maxCharacters");
    }
}
