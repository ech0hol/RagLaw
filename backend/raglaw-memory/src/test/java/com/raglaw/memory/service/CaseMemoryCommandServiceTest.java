package com.raglaw.memory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.memory.casefile.CaseScope;
import com.raglaw.memory.domain.CaseMemoryEntity;
import com.raglaw.memory.domain.CaseMemoryRepository;
import com.raglaw.memory.domain.CaseMemoryVersionEntity;
import com.raglaw.memory.domain.CaseMemoryVersionRepository;
import com.raglaw.memory.domain.MemoryAction;
import com.raglaw.memory.domain.MemoryAuditRepository;
import com.raglaw.memory.domain.MemorySourceType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CaseMemoryCommandServiceTest {
    private static final CaseScope SCOPE = new CaseScope("tenant", "user", "case");
    private static final Instant NOW = Instant.parse("2026-09-20T00:00:00Z");
    @Mock private CaseMemoryRepository memoryRepository;
    @Mock private MemoryAuditRepository auditRepository;
    @Mock private CaseMemoryVersionRepository versionRepository;
    private CaseMemoryCommandService service;

    @BeforeEach
    void setUp() {
        service = new CaseMemoryCommandService(memoryRepository, auditRepository, versionRepository,
                new MemoryAdmissionPolicy(), new MemoryConflictResolver(), new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void acceptedActionIncrementsSequenceAndWritesAudit() {
        CaseMemoryVersionEntity version = new CaseMemoryVersionEntity(SCOPE, NOW);
        when(versionRepository.findByTenantIdAndUserIdAndCaseId("tenant", "user", "case"))
                .thenReturn(Optional.of(version));
        when(memoryRepository.active(SCOPE)).thenReturn(List.of());
        when(memoryRepository.save(any(CaseMemoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(versionRepository.save(any(CaseMemoryVersionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MemoryCommandResult result = service.apply(SCOPE, candidate(), "user");

        assertThat(result.admitted()).isTrue();
        assertThat(result.resolution().action()).isEqualTo(MemoryAction.ADD);
        assertThat(result.caseSequence()).isEqualTo(1);
        verify(auditRepository).save(any());
    }

    @Test
    void rejectedCandidateDoesNotWriteMemory() {
        MemoryCommandResult result = service.apply(SCOPE,
                new MemoryCandidate("PERSON", "SELF", "NAME", "{}", null, null,
                        MemorySourceType.USER_MESSAGE, "", false, false), "user");
        assertThat(result.admitted()).isFalse();
        org.mockito.Mockito.verifyNoInteractions(memoryRepository, auditRepository, versionRepository);
    }

    private static MemoryCandidate candidate() {
        return new MemoryCandidate("EMPLOYEE", "SELF", "MONTHLY_SALARY", "{\"amount\":15000}",
                null, null, MemorySourceType.USER_MESSAGE, "message-1", false, false);
    }
}
