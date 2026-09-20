package com.raglaw.agentscope.domain;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
public interface WorkflowNodeRunRepository extends JpaRepository<WorkflowNodeRunEntity,String> {
    List<WorkflowNodeRunEntity> findByRunId(String runId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorkflowNodeRunEntity> findByRunIdAndNodeCodeAndIdempotencyKey(String runId, String nodeCode, String idempotencyKey);
}
