package com.raglaw.memory.casefile;

public record CaseScope(String tenantId, String userId, String caseId) {
    public CaseScope {
        require(tenantId, "tenantId");
        require(userId, "userId");
        require(caseId, "caseId");
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name);
        }
    }
}
