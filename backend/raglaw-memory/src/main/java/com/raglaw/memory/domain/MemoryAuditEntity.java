package com.raglaw.memory.domain;

import com.raglaw.memory.casefile.CaseScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "raglaw_memory_audit")
public class MemoryAuditEntity {
    @Id
    private String id;
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;
    @Column(name = "case_id", nullable = false, length = 36)
    private String caseId;
    @Column(name = "candidate_json", nullable = false, columnDefinition = "JSON")
    private String candidateJson;
    @Column(name = "proposed_action", nullable = false, length = 32)
    private MemoryAction proposedAction;
    @Column(name = "applied_action", nullable = false, length = 32)
    private MemoryAction appliedAction;
    @Column(nullable = false, length = 512)
    private String reason;
    @Column(nullable = false, length = 128)
    private String actor;
    @Column(name = "old_memory_ids_json", columnDefinition = "JSON")
    private String oldMemoryIdsJson;
    @Column(name = "new_memory_ids_json", columnDefinition = "JSON")
    private String newMemoryIdsJson;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MemoryAuditEntity() {
    }

    public MemoryAuditEntity(String id, CaseScope scope, String candidateJson, MemoryAction proposedAction,
                             MemoryAction appliedAction, String reason, String actor, String oldMemoryIdsJson,
                             String newMemoryIdsJson, Instant createdAt) {
        this.id = id;
        this.tenantId = scope.tenantId();
        this.userId = scope.userId();
        this.caseId = scope.caseId();
        this.candidateJson = candidateJson;
        this.proposedAction = proposedAction;
        this.appliedAction = appliedAction;
        this.reason = reason;
        this.actor = actor;
        this.oldMemoryIdsJson = oldMemoryIdsJson;
        this.newMemoryIdsJson = newMemoryIdsJson;
        this.createdAt = createdAt;
    }
}
