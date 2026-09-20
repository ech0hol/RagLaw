package com.raglaw.memory.domain;

import com.raglaw.memory.casefile.CaseScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "raglaw_case_memory_version")
@IdClass(CaseMemoryVersionId.class)
public class CaseMemoryVersionEntity {
    @Id
    @Column(name = "tenant_id", length = 64)
    private String tenantId;
    @Id
    @Column(name = "user_id", length = 36)
    private String userId;
    @Id
    @Column(name = "case_id", length = 36)
    private String caseId;
    @Column(name = "latest_sequence", nullable = false)
    private long latestSequence;
    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CaseMemoryVersionEntity() {
    }

    public CaseMemoryVersionEntity(CaseScope scope, Instant now) {
        this.tenantId = scope.tenantId();
        this.userId = scope.userId();
        this.caseId = scope.caseId();
        this.updatedAt = now;
    }

    public long nextSequence(Instant now) {
        latestSequence++;
        updatedAt = now;
        return latestSequence;
    }

    public long getLatestSequence() { return latestSequence; }
    public CaseScope scope() { return new CaseScope(tenantId, userId, caseId); }
}
