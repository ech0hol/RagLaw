package com.raglaw.memory.domain;

import com.raglaw.memory.casefile.CaseScope;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CaseMemoryRepository extends JpaRepository<CaseMemoryEntity, String> {
    List<CaseMemoryEntity> findByTenantIdAndUserIdAndCaseIdAndLifecycleOrderByCaseSequenceAsc(
            String tenantId, String userId, String caseId, MemoryLifecycle lifecycle);

    List<CaseMemoryEntity> findByTenantIdAndUserIdAndCaseIdAndCaseSequenceLessThanEqualAndLifecycleOrderByCaseSequenceAsc(
            String tenantId, String userId, String caseId, long caseSequence, MemoryLifecycle lifecycle);

    List<CaseMemoryEntity> findByTenantIdAndUserIdAndCaseIdAndSubjectTypeAndSubjectIdAndPredicate(
            String tenantId, String userId, String caseId, String subjectType, String subjectId, String predicate);

    Optional<CaseMemoryEntity> findByIdAndTenantIdAndUserIdAndCaseId(
            String id, String tenantId, String userId, String caseId);

    default List<CaseMemoryEntity> active(CaseScope scope) {
        return findByTenantIdAndUserIdAndCaseIdAndLifecycleOrderByCaseSequenceAsc(
                scope.tenantId(), scope.userId(), scope.caseId(), MemoryLifecycle.ACTIVE);
    }
}
