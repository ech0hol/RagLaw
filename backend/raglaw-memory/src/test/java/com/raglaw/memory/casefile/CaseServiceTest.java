package com.raglaw.memory.casefile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CaseServiceTest {

    @Mock
    private CaseRepository repository;

    private CaseService service;

    @BeforeEach
    void setUp() {
        service = new CaseService(repository, Clock.fixed(Instant.parse("2026-09-20T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void userCanCreateAndReadOwnedCase() {
        when(repository.save(any(CaseEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CaseEntity created = service.create("tenant-a", "user-a", "劳动争议");
        when(repository.findByIdAndTenantIdAndUserId(created.getId(), "tenant-a", "user-a"))
                .thenReturn(Optional.of(created));

        assertThat(service.get("tenant-a", "user-a", created.getId())).contains(created);
        verify(repository).save(any(CaseEntity.class));
    }

    @Test
    void differentUserCannotReadSameCase() {
        String caseId = "case-a";
        when(repository.findByIdAndTenantIdAndUserId(caseId, "tenant-a", "user-b"))
                .thenReturn(Optional.empty());

        assertThat(service.get("tenant-a", "user-b", caseId)).isEmpty();
        assertThat(service.existsOwnedBy("tenant-a", "user-b", caseId)).isFalse();
    }
}
