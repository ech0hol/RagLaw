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
    public String getId(){return id;} public void setId(String v){id=v;} public String getTraceId(){return traceId;} public void setTraceId(String v){traceId=v;} public String getRouteDecisionId(){return routeDecisionId;} public void setRouteDecisionId(String v){routeDecisionId=v;} public String getWorkflowCode(){return workflowCode;} public void setWorkflowCode(String v){workflowCode=v;} public String getStatus(){return status;} public void setStatus(String v){status=v;} public String getApprovalStatus(){return approvalStatus;} public void setApprovalStatus(String v){approvalStatus=v;} public Instant getStartedAt(){return startedAt;} public void setStartedAt(Instant v){startedAt=v;} public Instant getCompletedAt(){return completedAt;} public void setCompletedAt(Instant v){completedAt=v;} public String getErrorCode(){return errorCode;} public void setErrorCode(String v){errorCode=v;}
}
