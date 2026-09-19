package com.raglaw.agentscope.routing;

import java.util.List;
import java.util.Set;

public record RulePrecheckResult(Set<RiskSignal> hardSignals, List<String> missingMaterials, List<String> reasons) {
    public RulePrecheckResult {
        hardSignals = hardSignals == null ? Set.of() : Set.copyOf(hardSignals);
        missingMaterials = missingMaterials == null ? List.of() : List.copyOf(missingMaterials);
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
