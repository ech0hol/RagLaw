package com.raglaw.agentscope.workflow;

import java.util.List;
import java.util.Map;

public record WorkflowTaskNote(long revision, Map<String, List<String>> sections) {
    public WorkflowTaskNote {
        if (revision < 0) throw new IllegalArgumentException("revision");
        sections = sections == null ? Map.of() : sections.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }
}
