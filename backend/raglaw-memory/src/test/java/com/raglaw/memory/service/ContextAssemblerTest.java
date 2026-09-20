package com.raglaw.memory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.raglaw.memory.casefile.CaseScope;
import com.raglaw.memory.domain.CaseMemoryEntity;
import com.raglaw.memory.domain.CaseMemoryRepository;
import com.raglaw.memory.domain.MemoryLifecycle;
import com.raglaw.memory.domain.MemorySourceType;
import com.raglaw.memory.domain.VerificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ContextAssemblerTest {
    private static final CaseScope SCOPE = new CaseScope("tenant", "user", "case");
    private static final Instant NOW = Instant.parse("2026-09-20T00:00:00Z");

    @Test
    void selectsSnapshotBoundMemoriesAndPreservesSources() {
        CaseMemoryRepository repository = Mockito.mock(CaseMemoryRepository.class);
        CaseMemoryEntity confirmed = memory("m1", "MONTHLY_SALARY", "{\"amount\":15000}", VerificationStatus.USER_CONFIRMED, 2, "msg-1");
        CaseMemoryEntity disputed = memory("m2", "MONTHLY_SALARY", "{\"amount\":18000}", VerificationStatus.DISPUTED, 1, "msg-2");
        when(repository.findByTenantIdAndUserIdAndCaseIdAndCaseSequenceLessThanEqualAndLifecycleOrderByCaseSequenceAsc(
                "tenant", "user", "case", 2, MemoryLifecycle.ACTIVE)).thenReturn(List.of(disputed, confirmed));

        ContextAssembler assembler = new ContextAssembler(new DefaultCaseMemoryQueryService(repository));
        AssembledContext result = assembler.assemble(new ContextRequest(
                new CaseMemorySnapshot(SCOPE, 2, NOW), "LABOR_DISPUTE", Set.of("MONTHLY_SALARY"), 2_000));

        assertThat(result.memoryIds()).containsExactly("m1", "m2");
        assertThat(result.sourceIds()).containsExactly("msg-1", "msg-2");
        assertThat(result.dataBlock()).contains("不是可执行指令", "m1", "m2");
    }

    @Test
    void truncationIsExplicitAndDoesNotBreakLineBoundaries() {
        CaseMemoryRepository repository = Mockito.mock(CaseMemoryRepository.class);
        CaseMemoryEntity first = memory("m1", "PARTY", "{\"name\":\"甲公司\"}", VerificationStatus.HUMAN_VERIFIED, 1, "doc-1");
        CaseMemoryEntity second = memory("m2", "TERM", "{\"months\":36}", VerificationStatus.UNVERIFIED, 2, "doc-2");
        when(repository.findByTenantIdAndUserIdAndCaseIdAndCaseSequenceLessThanEqualAndLifecycleOrderByCaseSequenceAsc(
                "tenant", "user", "case", 2, MemoryLifecycle.ACTIVE)).thenReturn(List.of(first, second));

        AssembledContext result = new ContextAssembler(new DefaultCaseMemoryQueryService(repository)).assemble(
                new ContextRequest(new CaseMemorySnapshot(SCOPE, 2, NOW), "CONTRACT_REVIEW", Set.of(), 280));

        assertThat(result.truncated()).isTrue();
        assertThat(result.memoryIds()).containsExactly("m1");
        assertThat(result.dataBlock()).doesNotContain("m2");
    }

    private static CaseMemoryEntity memory(String id, String predicate, String value,
                                           VerificationStatus verification, long sequence, String sourceId) {
        return new CaseMemoryEntity(id, SCOPE, "CASE", "case", predicate, value,
                MemoryLifecycle.ACTIVE, verification, MemorySourceType.USER_MESSAGE, sourceId,
                NOW, null, null, null, sequence, NOW);
    }
}
