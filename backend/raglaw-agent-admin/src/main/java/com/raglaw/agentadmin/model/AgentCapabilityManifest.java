package com.raglaw.agentadmin.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record AgentCapabilityManifest(
        Set<String> domains,
        Set<String> capabilities,
        Set<String> supportedTaskTypes,
        Set<String> supportedRiskLevels,
        Set<String> requiredInputs,
        String outputSchema
) {
    public AgentCapabilityManifest {
        domains = normalizeRequired(domains, "domains");
        capabilities = normalizeRequired(capabilities, "capabilities");
        supportedTaskTypes = normalizeRequired(supportedTaskTypes, "supportedTaskTypes");
        supportedRiskLevels = normalizeRequired(supportedRiskLevels, "supportedRiskLevels");
        requiredInputs = normalizeOptional(requiredInputs);
        if (outputSchema == null || outputSchema.isBlank()) throw new IllegalArgumentException("outputSchema");
        outputSchema = outputSchema.trim();
    }

    private static Set<String> normalizeRequired(Set<String> values, String name) {
        Set<String> result = normalizeOptional(values);
        if (result.isEmpty()) throw new IllegalArgumentException(name);
        return result;
    }

    private static Set<String> normalizeOptional(Set<String> values) {
        if (values == null) return Set.of();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException("blank capability");
            result.add(value.trim().toUpperCase());
        }
        return Set.copyOf(result);
    }
}
