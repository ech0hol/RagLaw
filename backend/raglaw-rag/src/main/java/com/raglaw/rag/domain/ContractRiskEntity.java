package com.raglaw.rag.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "raglaw_contract_risk")
public class ContractRiskEntity {

    @Id
    private String id;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "chunk_id")
    private String chunkId;

    @Column(nullable = false, length = 16)
    private String severity;

    @Column(nullable = false, length = 64)
    private String dimension;

    @Column(nullable = false, length = 512)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String excerpt;

    @Column(columnDefinition = "TEXT")
    private String suggestion;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "highlight_rects_json", columnDefinition = "TEXT")
    private String highlightRectsJson;

    @Column(nullable = false)
    private boolean accepted;

    @Column(name = "revised_excerpt", columnDefinition = "TEXT")
    private String revisedExcerpt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ContractRiskEntity() {
    }

    public ContractRiskEntity(
            String id,
            String documentId,
            String chunkId,
            String severity,
            String dimension,
            String summary,
            String excerpt,
            String suggestion
    ) {
        this(id, documentId, chunkId, severity, dimension, summary, excerpt, suggestion, null);
    }

    public ContractRiskEntity(
            String id,
            String documentId,
            String chunkId,
            String severity,
            String dimension,
            String summary,
            String excerpt,
            String suggestion,
            Integer pageNumber
    ) {
        this.id = id;
        this.documentId = documentId;
        this.chunkId = chunkId;
        this.severity = severity;
        this.dimension = dimension;
        this.summary = summary;
        this.excerpt = excerpt;
        this.suggestion = suggestion;
        this.pageNumber = pageNumber;
        this.accepted = false;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getChunkId() {
        return chunkId;
    }

    public String getSeverity() {
        return severity;
    }

    public String getDimension() {
        return dimension;
    }

    public String getSummary() {
        return summary;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public String getHighlightRectsJson() {
        return highlightRectsJson;
    }

    public void setHighlightRectsJson(String highlightRectsJson) {
        this.highlightRectsJson = highlightRectsJson;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public String getRevisedExcerpt() {
        return revisedExcerpt;
    }

    public void setRevisedExcerpt(String revisedExcerpt) {
        this.revisedExcerpt = revisedExcerpt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
