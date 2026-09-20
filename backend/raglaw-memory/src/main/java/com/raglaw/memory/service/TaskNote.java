package com.raglaw.memory.service;

import java.util.List;

public record TaskNote(
        String objective,
        List<String> confirmedFactIds,
        List<String> decisionIds,
        List<String> unresolvedQuestions,
        List<String> sourceIds,
        long snapshotVersion
) {
    public TaskNote {
        objective = objective == null ? "" : objective.trim();
        confirmedFactIds = copy(confirmedFactIds);
        decisionIds = copy(decisionIds);
        unresolvedQuestions = copy(unresolvedQuestions);
        sourceIds = copy(sourceIds);
        if (snapshotVersion < 0) throw new IllegalArgumentException("snapshotVersion");
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }
}
