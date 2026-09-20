package com.raglaw.memory.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.raglaw.memory.casefile.CaseScope;
import com.raglaw.memory.domain.CaseMemoryEntity;
import com.raglaw.memory.domain.MemoryAction;
import com.raglaw.memory.domain.MemoryLifecycle;
import com.raglaw.memory.domain.MemorySourceType;
import com.raglaw.memory.domain.VerificationStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MemoryConflictResolverTest {
    private static final CaseScope SCOPE = new CaseScope("tenant", "user", "case");
    private static final Instant NOW = Instant.parse("2026-09-20T00:00:00Z");
    private final MemoryConflictResolver resolver = new MemoryConflictResolver();

    @Test
    void identicalSalaryIsNoOp() {
        CaseMemoryEntity existing = memory("m1", "MONTHLY_SALARY", "{\"amount\":15000}", MemorySourceType.USER_MESSAGE);
        assertThat(resolver.resolve(candidate("MONTHLY_SALARY", "{\"amount\":15000}", false, MemorySourceType.USER_MESSAGE), List.of(existing)).action())
                .isEqualTo(MemoryAction.NO_OP);
    }

    @Test
    void salaryBasisEnrichesSalary() {
        CaseMemoryEntity existing = memory("m1", "MONTHLY_SALARY", "{\"amount\":15000}", MemorySourceType.USER_MESSAGE);
        assertThat(resolver.resolve(candidate("TAX_BASIS", "{\"basis\":\"PRE_TAX\"}", false, MemorySourceType.USER_MESSAGE), List.of(existing)).action())
                .isEqualTo(MemoryAction.ENRICH);
    }

    @Test
    void explicitCorrectionSupersedesOldValue() {
        CaseMemoryEntity existing = memory("m1", "MONTHLY_SALARY", "{\"amount\":15000}", MemorySourceType.USER_MESSAGE);
        MemoryResolution resolution = resolver.resolve(candidate("MONTHLY_SALARY", "{\"amount\":18000}", true, MemorySourceType.USER_MESSAGE), List.of(existing));
        assertThat(resolution.action()).isEqualTo(MemoryAction.SUPERSEDE);
        assertThat(resolution.affectedMemoryIds()).containsExactly("m1");
    }

    @Test
    void independentSourcesAreDisputed() {
        CaseMemoryEntity existing = memory("m1", "MONTHLY_SALARY", "{\"amount\":15000}", MemorySourceType.CONTRACT);
        assertThat(resolver.resolve(candidate("MONTHLY_SALARY", "{\"amount\":18000}", false, MemorySourceType.USER_MESSAGE), List.of(existing)).action())
                .isEqualTo(MemoryAction.MARK_DISPUTED);
    }

    @Test
    void endDateInvalidatesOngoingStatus() {
        CaseMemoryEntity existing = memory("m1", "EMPLOYMENT_STATUS", "{\"value\":\"ACTIVE\"}", MemorySourceType.USER_MESSAGE);
        assertThat(resolver.resolve(candidate("EMPLOYMENT_END_DATE", "{\"date\":\"2026-09-30\"}", false, MemorySourceType.USER_MESSAGE), List.of(existing)).action())
                .isEqualTo(MemoryAction.INVALIDATE);
    }

    private static MemoryCandidate candidate(String predicate, String value, boolean correction, MemorySourceType source) {
        return new MemoryCandidate("EMPLOYEE", "SELF", predicate, value, null, null, source, "message-2", correction, false);
    }

    private static CaseMemoryEntity memory(String id, String predicate, String value, MemorySourceType source) {
        return new CaseMemoryEntity(id, SCOPE, "EMPLOYEE", "SELF", predicate, value, MemoryLifecycle.ACTIVE,
                VerificationStatus.UNVERIFIED, source, "source-1", null, null, null, null, 1, NOW);
    }
}
