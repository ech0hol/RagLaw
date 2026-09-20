package com.raglaw.agentscope.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "raglaw_agent_resolution_observation")
public class AgentResolutionObservationEntity {
    @Id private String id;
    @Column(name = "trace_id", nullable = false, length = 36) private String traceId;
    @Column(name = "role_code", nullable = false, length = 128) private String roleCode;
    @Column(name = "deterministic_winner", nullable = false, length = 128) private String deterministicWinner;
    @Column(name = "model_winner", length = 128) private String modelWinner;
    @Column(nullable = false) private boolean agreement;
    @Column private double confidence;
    @Column(name = "candidate_versions_json", nullable = false, columnDefinition = "JSON") private String candidateVersionsJson;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected AgentResolutionObservationEntity() {}
    public AgentResolutionObservationEntity(String id, String traceId, String roleCode, String deterministicWinner,
                                            String modelWinner, boolean agreement, double confidence,
                                            String candidateVersionsJson, Instant createdAt) {
        this.id = id; this.traceId = traceId; this.roleCode = roleCode; this.deterministicWinner = deterministicWinner;
        this.modelWinner = modelWinner; this.agreement = agreement; this.confidence = confidence;
        this.candidateVersionsJson = candidateVersionsJson; this.createdAt = createdAt;
    }
    public String getId() { return id; }
    public String getTraceId() { return traceId; }
    public String getRoleCode() { return roleCode; }
    public String getDeterministicWinner() { return deterministicWinner; }
    public String getModelWinner() { return modelWinner; }
    public boolean isAgreement() { return agreement; }
    public double getConfidence() { return confidence; }
    public String getCandidateVersionsJson() { return candidateVersionsJson; }
    public Instant getCreatedAt() { return createdAt; }
}
