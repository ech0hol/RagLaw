package com.raglaw.agentscope.routing;

import java.util.List;
import java.util.Set;

public record TaskClassification(
        TaskType taskType,
        Set<RiskSignal> riskSignals,
        double confidence,
        List<String> missingMaterials,
        String rationale,
        String promptVersion,
        String modelVersion
) {

    public TaskClassification {
        if (!(confidence >= 0.0 && confidence <= 1.0)) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        riskSignals = riskSignals == null ? Set.of() : Set.copyOf(riskSignals);
        missingMaterials = missingMaterials == null ? List.of() : List.copyOf(missingMaterials);
    }
}
