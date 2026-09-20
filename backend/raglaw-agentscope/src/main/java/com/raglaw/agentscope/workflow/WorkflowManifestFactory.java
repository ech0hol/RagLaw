package com.raglaw.agentscope.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.agentscope.routing.WorkflowCatalog;
import com.raglaw.memory.service.CaseMemorySnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class WorkflowManifestFactory {
    public record ManifestRequest(String runId, String tenantId, String userId, String caseId, String conversationId,
                                  WorkflowDefinition workflow, int workflowVersion, String routeDecisionId,
                                  CaseMemorySnapshot memorySnapshot, String riskPolicyVersion, String toolPolicyVersion,
                                  Map<String, ResolvedAgent> resolvedAgents, Set<String> availableMaterials) {}

    private final ObjectMapper objectMapper;
    public WorkflowManifestFactory(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public WorkflowExecutionManifest create(ManifestRequest request) {
        if (request == null || request.workflow() == null || request.memorySnapshot() == null) throw new IllegalArgumentException("manifest request");
        if (!request.tenantId().equals(request.memorySnapshot().scope().tenantId()) || !request.userId().equals(request.memorySnapshot().scope().userId()) || !request.caseId().equals(request.memorySnapshot().scope().caseId())) throw new IllegalArgumentException("case authorization mismatch");
        Set<String> materials = request.availableMaterials() == null ? Set.of() : request.availableMaterials();
        if (!materials.containsAll(request.workflow().requiredMaterials())) throw new IllegalArgumentException("missing workflow materials");
        Map<String, ResolvedWorkflowNode> nodes = new LinkedHashMap<>();
        for (WorkflowNodeDefinition node : request.workflow().nodes()) {
            RoleRequirement role = node.roleRequirement();
            ResolvedAgent agent = request.resolvedAgents() == null ? null : request.resolvedAgents().get(role.roleCode());
            if (agent == null) throw new IllegalArgumentException("missing resolved role: " + role.roleCode());
            nodes.put(node.code(), new ResolvedWorkflowNode(node.code(), role.roleCode(), agent.agentCode(), agent.agentVersion(), agent.effectiveTools(), "", node.dependsOn()));
        }
        String checksum = checksum(nodes, request.workflow().code(), request.memorySnapshot().version(), request.toolPolicyVersion());
        return new WorkflowExecutionManifest(request.runId(), request.tenantId(), request.userId(), request.caseId(), request.conversationId(), request.workflow().code(), request.workflowVersion(), request.routeDecisionId(), request.memorySnapshot().version(), request.riskPolicyVersion(), request.toolPolicyVersion(), nodes, checksum);
    }

    private String checksum(Map<String, ResolvedWorkflowNode> nodes, String workflow, long memoryVersion, String toolPolicyVersion) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(Map.of("workflow", workflow, "memory", memoryVersion, "tools", toolPolicyVersion, "nodes", nodes));
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(); for (byte value : digest) result.append(String.format("%02x", value)); return result.toString();
        } catch (Exception exception) { throw new IllegalStateException("manifest checksum failed", exception); }
    }
}
