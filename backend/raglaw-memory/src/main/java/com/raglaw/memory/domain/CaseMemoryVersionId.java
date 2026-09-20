package com.raglaw.memory.domain;

import java.io.Serializable;
import java.util.Objects;

public class CaseMemoryVersionId implements Serializable {
    private String tenantId;
    private String userId;
    private String caseId;

    public CaseMemoryVersionId() {
    }

    public CaseMemoryVersionId(String tenantId, String userId, String caseId) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.caseId = caseId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CaseMemoryVersionId that)) return false;
        return Objects.equals(tenantId, that.tenantId) && Objects.equals(userId, that.userId)
                && Objects.equals(caseId, that.caseId);
    }

    @Override
    public int hashCode() { return Objects.hash(tenantId, userId, caseId); }
}
