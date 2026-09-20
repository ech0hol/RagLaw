package com.raglaw.memory.domain;

import com.raglaw.memory.casefile.CaseScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "raglaw_case_memory")
public class CaseMemoryEntity {

    @Id
    private String id;
    @Column(name = "tenant_id", nullable = false, length = 64)
    private String tenantId;
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;
    @Column(name = "case_id", nullable = false, length = 36)
    private String caseId;
    @Column(name = "subject_type", nullable = false, length = 64)
    private String subjectType;
    @Column(name = "subject_id", nullable = false, length = 128)
    private String subjectId;
    @Column(nullable = false, length = 128)
    private String predicate;
    @Column(name = "value_json", nullable = false, columnDefinition = "JSON")
    private String valueJson;
    @Column(nullable = false, length = 32)
    private MemoryLifecycle lifecycle;
    @Column(nullable = false, length = 32)
    private VerificationStatus verification;
    @Column(name = "source_type", nullable = false, length = 32)
    private MemorySourceType sourceType;
    @Column(name = "source_id", nullable = false, length = 128)
    private String sourceId;
    @Column(name = "valid_from")
    private Instant validFrom;
    @Column(name = "valid_to")
    private Instant validTo;
    @Column(name = "supersedes_id", length = 36)
    private String supersedesId;
    @Column(name = "replacement_memory_id", length = 36)
    private String replacementMemoryId;
    @Column(name = "case_sequence", nullable = false)
    private long caseSequence;
    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CaseMemoryEntity() {
    }

    public CaseMemoryEntity(
            String id,
            CaseScope scope,
            String subjectType,
            String subjectId,
            String predicate,
            String valueJson,
            MemoryLifecycle lifecycle,
            VerificationStatus verification,
            MemorySourceType sourceType,
            String sourceId,
            Instant validFrom,
            Instant validTo,
            String supersedesId,
            String replacementMemoryId,
            long caseSequence,
            Instant now
    ) {
        this.id = require(id, "id");
        this.tenantId = scope.tenantId();
        this.userId = scope.userId();
        this.caseId = scope.caseId();
        this.subjectType = require(subjectType, "subjectType");
        this.subjectId = require(subjectId, "subjectId");
        this.predicate = require(predicate, "predicate");
        this.valueJson = require(valueJson, "valueJson");
        this.lifecycle = require(lifecycle, "lifecycle");
        this.verification = require(verification, "verification");
        this.sourceType = require(sourceType, "sourceType");
        this.sourceId = sourceId;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.supersedesId = supersedesId;
        this.replacementMemoryId = replacementMemoryId;
        this.caseSequence = caseSequence;
        this.createdAt = now;
        this.updatedAt = now;
        validateInvariants();
    }

    @PrePersist
    @PreUpdate
    void validateInvariants() {
        if (lifecycle == MemoryLifecycle.ACTIVE && isBlank(sourceId)) {
            throw new IllegalStateException("active memory requires sourceId");
        }
        if (lifecycle == MemoryLifecycle.SUPERSEDED && isBlank(replacementMemoryId)) {
            throw new IllegalStateException("superseded memory requires replacementMemoryId");
        }
        if (validFrom != null && validTo != null && validTo.isBefore(validFrom)) {
            throw new IllegalStateException("validTo cannot precede validFrom");
        }
    }

    public CaseScope scope() { return new CaseScope(tenantId, userId, caseId); }
    public String getId() { return id; }
    public String getSubjectType() { return subjectType; }
    public String getSubjectId() { return subjectId; }
    public String getPredicate() { return predicate; }
    public String getValueJson() { return valueJson; }
    public MemoryLifecycle getLifecycle() { return lifecycle; }
    public VerificationStatus getVerification() { return verification; }
    public MemorySourceType getSourceType() { return sourceType; }
    public String getSourceId() { return sourceId; }
    public Instant getValidFrom() { return validFrom; }
    public Instant getValidTo() { return validTo; }
    public String getSupersedesId() { return supersedesId; }
    public String getReplacementMemoryId() { return replacementMemoryId; }
    public long getCaseSequence() { return caseSequence; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean isActive() { return lifecycle == MemoryLifecycle.ACTIVE; }

    private static String require(String value, String name) {
        if (isBlank(value)) throw new IllegalArgumentException(name);
        return value;
    }

    private static <T> T require(T value, String name) {
        if (value == null) throw new IllegalArgumentException(name);
        return value;
    }

    private static boolean isBlank(String value) { return value == null || value.isBlank(); }
}
