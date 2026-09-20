package com.raglaw.agentadmin.service;

import com.raglaw.agentadmin.model.AgentToolPolicy;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class EffectiveToolPolicy {
    public Set<String> resolve(AgentToolPolicy agentPolicy, Set<String> nodeTools,
                               Set<String> userTools, Set<String> riskTools) {
        if (agentPolicy == null) return Set.of();
        Set<String> result = new HashSet<>(normalize(agentPolicy.toolNames()));
        result.retainAll(normalize(nodeTools));
        result.retainAll(normalize(userTools));
        result.retainAll(normalize(riskTools));
        return Set.copyOf(result);
    }

    private static Set<String> normalize(Set<String> values) {
        if (values == null) return Set.of();
        return values.stream().filter(value -> value != null && !value.isBlank())
                .map(String::trim).collect(Collectors.toSet());
    }
}
