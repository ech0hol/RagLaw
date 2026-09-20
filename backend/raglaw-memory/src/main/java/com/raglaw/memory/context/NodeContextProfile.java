package com.raglaw.memory.context;

import java.util.List;
import java.util.Map;

public record NodeContextProfile(String code, Map<ContextSectionType, ContextPriority> priorities,
                                 List<ContextSectionType> sectionOrder) {
    public NodeContextProfile {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("code");
        priorities = priorities == null ? Map.of() : Map.copyOf(priorities);
        sectionOrder = sectionOrder == null ? List.of() : List.copyOf(sectionOrder);
    }
    public ContextPriority priority(ContextSectionType type) { return priorities.getOrDefault(type, ContextPriority.P3_OPTIONAL); }
}
