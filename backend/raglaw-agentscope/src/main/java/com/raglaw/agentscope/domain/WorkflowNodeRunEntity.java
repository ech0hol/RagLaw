package com.raglaw.agentscope.domain;

import jakarta.persistence.*;

@Entity @Table(name="raglaw_workflow_node_run", indexes={@Index(name="idx_workflow_node_run_run",columnList="run_id"), @Index(name="idx_workflow_node_code",columnList="run_id,node_code")}, uniqueConstraints=@UniqueConstraint(name="uk_workflow_node_idempotency", columnNames={"run_id", "node_code", "idempotency_key"}))
public class WorkflowNodeRunEntity {
    @Id @Column(length=36) private String id;
    @Column(name="run_id",nullable=false,length=36) private String runId;
    @Column(name="node_code",nullable=false,length=128) private String nodeCode;
    @Column(nullable=false) private int attempt;
    @Column(nullable=false,length=32) private String status;
    @Column(name="structured_output_json",columnDefinition="TEXT") private String structuredOutputJson;
    @Column(name="evidence_ids_json",columnDefinition="TEXT") private String evidenceIdsJson;
    @Column(name="latency_ms") private long latencyMs;
    @Column(name="error_code",length=128) private String errorCode;
    @Column(name="role_code",length=128) private String roleCode;
    @Column(name="agent_code",length=128) private String agentCode;
    @Column(name="agent_version") private Integer agentVersion;
    @Column(name="node_session_id",length=36) private String nodeSessionId;
    @Column(name="idempotency_key",length=128) private String idempotencyKey;
    @Column(name="input_refs_json",columnDefinition="JSON") private String inputRefsJson;
    @Column(name="output_revision") private Long outputRevision;
    @Column(name="started_at") private java.time.Instant startedAt;
    @Column(name="completed_at") private java.time.Instant completedAt;
    @Column(name="tool_call_summary_json",columnDefinition="JSON") private String toolCallSummaryJson;
    @Column(name="payload_hash",length=128) private String payloadHash;
    @Column(name="snapshot_version") private Long snapshotVersion;
    @Column(name="owner_id",length=128) private String ownerId;
    @Column(name="lease_until") private java.time.Instant leaseUntil;
    public String getId(){return id;} public void setId(String v){id=v;} public String getRunId(){return runId;} public void setRunId(String v){runId=v;} public String getNodeCode(){return nodeCode;} public void setNodeCode(String v){nodeCode=v;} public int getAttempt(){return attempt;} public void setAttempt(int v){attempt=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;} public String getStructuredOutputJson(){return structuredOutputJson;} public void setStructuredOutputJson(String v){structuredOutputJson=v;} public String getEvidenceIdsJson(){return evidenceIdsJson;} public void setEvidenceIdsJson(String v){evidenceIdsJson=v;} public long getLatencyMs(){return latencyMs;} public void setLatencyMs(long v){latencyMs=v;} public String getErrorCode(){return errorCode;} public void setErrorCode(String v){errorCode=v;}
    public String getRoleCode(){return roleCode;} public void setRoleCode(String v){roleCode=v;} public String getAgentCode(){return agentCode;} public void setAgentCode(String v){agentCode=v;} public Integer getAgentVersion(){return agentVersion;} public void setAgentVersion(Integer v){agentVersion=v;} public String getNodeSessionId(){return nodeSessionId;} public void setNodeSessionId(String v){nodeSessionId=v;} public String getIdempotencyKey(){return idempotencyKey;} public void setIdempotencyKey(String v){idempotencyKey=v;} public String getInputRefsJson(){return inputRefsJson;} public void setInputRefsJson(String v){inputRefsJson=v;} public Long getOutputRevision(){return outputRevision;} public void setOutputRevision(Long v){outputRevision=v;} public java.time.Instant getStartedAt(){return startedAt;} public void setStartedAt(java.time.Instant v){startedAt=v;} public java.time.Instant getCompletedAt(){return completedAt;} public void setCompletedAt(java.time.Instant v){completedAt=v;} public String getToolCallSummaryJson(){return toolCallSummaryJson;} public void setToolCallSummaryJson(String v){toolCallSummaryJson=v;} public String getPayloadHash(){return payloadHash;} public void setPayloadHash(String v){payloadHash=v;} public Long getSnapshotVersion(){return snapshotVersion;} public void setSnapshotVersion(Long v){snapshotVersion=v;}
    public String getOwnerId(){return ownerId;} public void setOwnerId(String v){ownerId=v;} public java.time.Instant getLeaseUntil(){return leaseUntil;} public void setLeaseUntil(java.time.Instant v){leaseUntil=v;}
}
