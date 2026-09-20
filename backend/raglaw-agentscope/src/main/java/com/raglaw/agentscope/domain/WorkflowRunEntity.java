package com.raglaw.agentscope.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity @Table(name="raglaw_workflow_run", indexes={@Index(name="idx_workflow_run_trace",columnList="trace_id"), @Index(name="idx_workflow_run_route",columnList="route_decision_id")})
public class WorkflowRunEntity {
    @Id @Column(length=36) private String id;
    @Column(name="trace_id",nullable=false,length=36) private String traceId;
    @Column(name="route_decision_id",nullable=false,length=36) private String routeDecisionId;
    @Column(name="workflow_code",nullable=false,length=128) private String workflowCode;
    @Column(nullable=false,length=32) private String status;
    @Column(name="approval_status",nullable=false,length=32) private String approvalStatus;
    @Column(name="started_at",nullable=false) private Instant startedAt;
    @Column(name="completed_at") private Instant completedAt;
    @Column(name="error_code",length=128) private String errorCode;
    @Column(name="tenant_id",length=64) private String tenantId;
    @Column(name="user_id",length=36) private String userId;
    @Column(name="case_id",length=36) private String caseId;
    @Column(name="conversation_id",length=36) private String conversationId;
    @Column(name="workflow_version") private Integer workflowVersion;
    @Column(name="memory_snapshot_version") private Long memorySnapshotVersion;
    @Column(name="manifest_json",columnDefinition="JSON") private String manifestJson;
    @Column(name="workflow_definition_json",columnDefinition="JSON") private String workflowDefinitionJson;
    @Column(name="workflow_definition_hash",length=128) private String workflowDefinitionHash;
    @Column(name="input_hash",length=128) private String inputHash;
    @Version @Column(name="lock_version",nullable=false) private long lockVersion;
    public String getId(){return id;} public void setId(String v){id=v;} public String getTraceId(){return traceId;} public void setTraceId(String v){traceId=v;} public String getRouteDecisionId(){return routeDecisionId;} public void setRouteDecisionId(String v){routeDecisionId=v;} public String getWorkflowCode(){return workflowCode;} public void setWorkflowCode(String v){workflowCode=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;} public String getApprovalStatus(){return approvalStatus;} public void setApprovalStatus(String v){approvalStatus=v;} public Instant getStartedAt(){return startedAt;} public void setStartedAt(Instant v){startedAt=v;} public Instant getCompletedAt(){return completedAt;} public void setCompletedAt(Instant v){completedAt=v;} public String getErrorCode(){return errorCode;} public void setErrorCode(String v){errorCode=v;}
    public String getTenantId(){return tenantId;} public void setTenantId(String v){tenantId=v;} public String getUserId(){return userId;} public void setUserId(String v){userId=v;} public String getCaseId(){return caseId;} public void setCaseId(String v){caseId=v;} public String getConversationId(){return conversationId;} public void setConversationId(String v){conversationId=v;} public Integer getWorkflowVersion(){return workflowVersion;} public void setWorkflowVersion(Integer v){workflowVersion=v;} public Long getMemorySnapshotVersion(){return memorySnapshotVersion;} public void setMemorySnapshotVersion(Long v){memorySnapshotVersion=v;} public String getManifestJson(){return manifestJson;} public void setManifestJson(String v){manifestJson=v;} public long getLockVersion(){return lockVersion;} public void setLockVersion(long v){lockVersion=v;}
    public String getWorkflowDefinitionJson(){return workflowDefinitionJson;} public void setWorkflowDefinitionJson(String v){workflowDefinitionJson=v;}
    public String getWorkflowDefinitionHash(){return workflowDefinitionHash;} public void setWorkflowDefinitionHash(String v){workflowDefinitionHash=v;}
    public String getInputHash(){return inputHash;} public void setInputHash(String v){inputHash=v;}
}
