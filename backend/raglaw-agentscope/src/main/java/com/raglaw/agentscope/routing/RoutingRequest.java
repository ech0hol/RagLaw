package com.raglaw.agentscope.routing;

public record RoutingRequest(
        String tenantId,
        String userId,
        String caseId,
        String conversationId,
        String query,
        boolean hasContractDocument,
        boolean mayUseExternalSearch
) {

    public RoutingRequest {
        requireId(tenantId, "tenantId");
        requireId(userId, "userId");
        requireId(caseId, "caseId");
        requireId(conversationId, "conversationId");
    }

    private static void requireId(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
