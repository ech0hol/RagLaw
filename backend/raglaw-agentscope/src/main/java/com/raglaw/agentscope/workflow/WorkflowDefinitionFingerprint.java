package com.raglaw.agentscope.workflow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.stream.Collectors;

/** Stable semantic identity for a workflow DAG; excludes non-authoritative object ordering. */
public final class WorkflowDefinitionFingerprint {
    private WorkflowDefinitionFingerprint() { }

    public static String hash(WorkflowDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("workflow");
        String canonical = definition.code() + "|tasks=" + definition.supportedTasks().stream().map(Enum::name).sorted().collect(Collectors.joining(","))
                + "|materials=" + definition.requiredMaterials().stream().sorted().collect(Collectors.joining(","))
                + "|nodes=" + definition.nodes().stream().sorted(Comparator.comparing(WorkflowNodeDefinition::code))
                .map(node -> node.code() + ":" + node.requiredRole() + ":" + node.dependsOn().stream().sorted().collect(Collectors.joining(","))
                        + ":parallel=" + node.parallelEligible())
                .collect(Collectors.joining(";"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("workflow definition hashing failed", exception);
        }
    }
}
