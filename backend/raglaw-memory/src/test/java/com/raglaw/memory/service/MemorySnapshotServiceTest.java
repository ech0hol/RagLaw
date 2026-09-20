package com.raglaw.memory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.raglaw.memory.casefile.CaseScope;
import com.raglaw.memory.domain.CaseMemoryVersionEntity;
import com.raglaw.memory.domain.CaseMemoryVersionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MemorySnapshotServiceTest {
    @Test
    void freezeKeepsVersionWhenLaterWritesHappen() {
        CaseMemoryVersionRepository repository = Mockito.mock(CaseMemoryVersionRepository.class);
        CaseScope scope = new CaseScope("tenant", "user", "case");
        CaseMemoryVersionEntity version = new CaseMemoryVersionEntity(scope, Instant.parse("2026-09-20T00:00:00Z"));
        version.nextSequence(Instant.parse("2026-09-20T00:00:01Z"));
        when(repository.findByTenantIdAndUserIdAndCaseId("tenant", "user", "case"))
                .thenReturn(Optional.of(version));

        DefaultMemorySnapshotService service = new DefaultMemorySnapshotService(repository,
                Clock.fixed(Instant.parse("2026-09-20T00:00:05Z"), ZoneOffset.UTC));
        CaseMemorySnapshot snapshot = service.freeze(scope);
        version.nextSequence(Instant.parse("2026-09-20T00:00:06Z"));

        assertThat(snapshot.version()).isEqualTo(1);
        assertThat(snapshot.createdAt()).isEqualTo(Instant.parse("2026-09-20T00:00:05Z"));
    }
}
