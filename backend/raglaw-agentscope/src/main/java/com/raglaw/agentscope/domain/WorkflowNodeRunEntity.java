package com.raglaw.agentscope.domain;

import jakarta.persistence.*;

@Entity @Table(name="raglaw_workflow_node_run", indexes={@Index(name="idx_workflow_node_run_run",columnList="run_id"), @Index(name="idx_workflow_node_code",columnList="run_id,node_code")})
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
    public String getId(){return id;} public void setId(String v){id=v;} public String getRunId(){return runId;} public void setRunId(String v){runId=v;} public String getNodeCode(){return nodeCode;} public void setNodeCode(String v){nodeCode=v;} public int getAttempt(){return attempt;} public void setAttempt(int v){attempt=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;} public String getStructuredOutputJson(){return structuredOutputJson;} public void setStructuredOutputJson(String v){structuredOutputJson=v;} public String getEvidenceIdsJson(){return evidenceIdsJson;} public void setEvidenceIdsJson(String v){evidenceIdsJson=v;} public long getLatencyMs(){return latencyMs;} public void setLatencyMs(long v){latencyMs=v;} public String getErrorCode(){return errorCode;} public void setErrorCode(String v){errorCode=v;}
}
