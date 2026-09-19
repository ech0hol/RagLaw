package com.raglaw.agentscope.domain;

import com.raglaw.common.jpa.ColumnLengths;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "raglaw_task_route_decision")
public class TaskRouteDecisionEntity {
    @Id @Column(length = ColumnLengths.UUID) private String id;
    @Column(name = "trace_id", length = ColumnLengths.UUID, nullable = false) private String traceId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "actual_expert_code", length = 128, nullable = false) private String actualExpertCode;
    @Column(name = "actual_expert_name", length = 256) private String actualExpertName;
    @Column(name = "candidate_task_type", length = 64, nullable = false) private String candidateTaskType;
    @Column(name = "risk_level", length = 32, nullable = false) private String riskLevel;
    @Column(name = "execution_mode", length = 48, nullable = false) private String executionMode;
    @Column(name = "expert_role", length = 128) private String expertRole;
    @Column(name = "workflow_code", length = 128) private String workflowCode;
    @Column(name = "agreement", nullable = false) private Boolean agreement;
    @Column(name = "classifier_latency_ms") private Long classifierLatencyMs;
    @Column(name = "prompt_version", length = 128) private String promptVersion;
    @Column(name = "model_version", length = 128) private String modelVersion;
    @Column(name = "policy_version", length = 128) private String policyVersion;
    @Column(name = "policy_reasons_json", columnDefinition = "TEXT") private String policyReasonsJson;
    @Column(name = "missing_materials_json", columnDefinition = "TEXT") private String missingMaterialsJson;
    @Column(name = "error_code", length = 128) private String errorCode;

    public TaskRouteDecisionEntity() { }
    public String getId() { return id; } public void setId(String v) { id = v; }
    public String getTraceId() { return traceId; } public void setTraceId(String v) { traceId = v; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant v) { createdAt = v; }
    public String getActualExpertCode() { return actualExpertCode; } public void setActualExpertCode(String v) { actualExpertCode = v; }
    public String getActualExpertName() { return actualExpertName; } public void setActualExpertName(String v) { actualExpertName = v; }
    public String getCandidateTaskType() { return candidateTaskType; } public void setCandidateTaskType(String v) { candidateTaskType = v; }
    public String getRiskLevel() { return riskLevel; } public void setRiskLevel(String v) { riskLevel = v; }
    public String getExecutionMode() { return executionMode; } public void setExecutionMode(String v) { executionMode = v; }
    public String getExpertRole() { return expertRole; } public void setExpertRole(String v) { expertRole = v; }
    public String getWorkflowCode() { return workflowCode; } public void setWorkflowCode(String v) { workflowCode = v; }
    public Boolean getAgreement() { return agreement; } public void setAgreement(Boolean v) { agreement = v; }
    public Long getClassifierLatencyMs() { return classifierLatencyMs; } public void setClassifierLatencyMs(Long v) { classifierLatencyMs = v; }
    public String getPromptVersion() { return promptVersion; } public void setPromptVersion(String v) { promptVersion = v; }
    public String getModelVersion() { return modelVersion; } public void setModelVersion(String v) { modelVersion = v; }
    public String getPolicyVersion() { return policyVersion; } public void setPolicyVersion(String v) { policyVersion = v; }
    public String getPolicyReasonsJson() { return policyReasonsJson; } public void setPolicyReasonsJson(String v) { policyReasonsJson = v; }
    public String getMissingMaterialsJson() { return missingMaterialsJson; } public void setMissingMaterialsJson(String v) { missingMaterialsJson = v; }
    public String getErrorCode() { return errorCode; } public void setErrorCode(String v) { errorCode = v; }
}
