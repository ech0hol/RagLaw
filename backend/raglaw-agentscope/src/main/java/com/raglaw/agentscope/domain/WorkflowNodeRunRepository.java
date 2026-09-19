package com.raglaw.agentscope.domain;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface WorkflowNodeRunRepository extends JpaRepository<WorkflowNodeRunEntity,String> { List<WorkflowNodeRunEntity> findByRunId(String runId); }
