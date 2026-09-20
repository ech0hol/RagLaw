package com.raglaw.agentscope.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowConflictRepository extends JpaRepository<WorkflowConflictEntity, String> {
    List<WorkflowConflictEntity> findByRunId(String runId);
}
