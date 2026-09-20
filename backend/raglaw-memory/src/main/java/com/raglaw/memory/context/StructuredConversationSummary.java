package com.raglaw.memory.context;

import java.util.List;

public record StructuredConversationSummary(String objective, List<String> confirmedFacts, List<String> disputedFacts,
                                            List<String> constraints, List<String> decisions, List<String> openQuestions,
                                            List<String> evidenceRefs, List<String> nextActions) {
    public StructuredConversationSummary {
        objective = objective == null ? "" : objective;
        confirmedFacts = copy(confirmedFacts); disputedFacts = copy(disputedFacts); constraints = copy(constraints);
        decisions = copy(decisions); openQuestions = copy(openQuestions); evidenceRefs = copy(evidenceRefs); nextActions = copy(nextActions);
    }
    private static List<String> copy(List<String> values) { return values == null ? List.of() : values.stream().filter(v -> v != null && !v.isBlank()).toList(); }
}
