package com.raglaw.memory.context;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ContextProfileCatalog {
    private final Map<String, NodeContextProfile> profiles;
    public ContextProfileCatalog() {
        EnumMap<ContextSectionType, ContextPriority> legal = new EnumMap<>(ContextSectionType.class);
        legal.put(ContextSectionType.GOVERNANCE, ContextPriority.P0_REQUIRED);
        legal.put(ContextSectionType.ROLE_CONTRACT, ContextPriority.P0_REQUIRED);
        legal.put(ContextSectionType.CURRENT_TASK, ContextPriority.P0_REQUIRED);
        legal.put(ContextSectionType.CASE_FACTS, ContextPriority.P1_HIGH);
        legal.put(ContextSectionType.DISPUTED_FACTS, ContextPriority.P1_HIGH);
        legal.put(ContextSectionType.EVIDENCE, ContextPriority.P1_HIGH);
        legal.put(ContextSectionType.TASK_NOTE, ContextPriority.P1_HIGH);
        legal.put(ContextSectionType.DEPENDENCY_RESULT, ContextPriority.P1_HIGH);
        legal.put(ContextSectionType.LEGAL_AUTHORITY, ContextPriority.P2_COMPRESSIBLE);
        legal.put(ContextSectionType.RECENT_CONVERSATION, ContextPriority.P2_COMPRESSIBLE);
        legal.put(ContextSectionType.HISTORY_RECALL, ContextPriority.P2_COMPRESSIBLE);
        legal.put(ContextSectionType.TOOL_ARTIFACT, ContextPriority.P3_OPTIONAL);
        profiles = Map.of("SINGLE_ADVISOR", new NodeContextProfile("SINGLE_ADVISOR", legal, List.of(ContextSectionType.GOVERNANCE, ContextSectionType.ROLE_CONTRACT, ContextSectionType.CURRENT_TASK, ContextSectionType.CASE_FACTS, ContextSectionType.EVIDENCE, ContextSectionType.RECENT_CONVERSATION)),
                "LEGAL_ANALYSIS", new NodeContextProfile("LEGAL_ANALYSIS", legal, List.of(ContextSectionType.GOVERNANCE, ContextSectionType.ROLE_CONTRACT, ContextSectionType.CURRENT_TASK, ContextSectionType.CASE_FACTS, ContextSectionType.DISPUTED_FACTS, ContextSectionType.EVIDENCE, ContextSectionType.TASK_NOTE, ContextSectionType.DEPENDENCY_RESULT, ContextSectionType.LEGAL_AUTHORITY, ContextSectionType.RECENT_CONVERSATION)));
    }
    public NodeContextProfile require(String code) { return profiles.getOrDefault(code, profiles.get("SINGLE_ADVISOR")); }
}
