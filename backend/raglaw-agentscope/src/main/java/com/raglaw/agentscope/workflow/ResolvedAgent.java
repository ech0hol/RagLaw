package com.raglaw.agentscope.workflow;

import java.util.List;
import java.util.Set;

public record ResolvedAgent(String roleCode, String agentCode, int agentVersion, Set<String> effectiveTools,
                            String configChecksum, double score, List<String> reasons) {
    public ResolvedAgent {
        effectiveTools = effectiveTools == null ? Set.of() : Set.copyOf(effectiveTools);
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
