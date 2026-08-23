package com.raglaw.rag.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "raglaw_case_statute_ref")
public class CaseStatuteRefEntity {

    @Id
    private String id;

    @Column(name = "case_doc_id", nullable = false)
    private String caseDocId;

    @Column(name = "statute_id", nullable = false)
    private String statuteId;

    @Column(name = "ref_type", nullable = false)
    private String refType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CaseStatuteRefEntity() {
    }

    public CaseStatuteRefEntity(String id, String caseDocId, String statuteId, String refType) {
        this.id = id;
        this.caseDocId = caseDocId;
        this.statuteId = statuteId;
        this.refType = refType;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getCaseDocId() {
        return caseDocId;
    }

    public String getStatuteId() {
        return statuteId;
    }

    public String getRefType() {
        return refType;
    }
}
