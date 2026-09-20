package com.raglaw.memory.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.raglaw.memory.casefile.CaseScope;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CaseMemoryEntityTest {
    private static final CaseScope SCOPE = new CaseScope("tenant", "user", "case");
    private static final Instant NOW = Instant.parse("2026-09-20T00:00:00Z");

    @Test
    void activeMemoryRequiresSource() {
        assertThrows(IllegalStateException.class, () -> new CaseMemoryEntity(
                "memory", SCOPE, "PERSON", "SELF", "NAME", "{\"value\":\"A\"}",
                MemoryLifecycle.ACTIVE, VerificationStatus.UNVERIFIED, MemorySourceType.USER_MESSAGE,
                "", null, null, null, null, 1, NOW));
    }

    @Test
    void supersededMemoryRequiresReplacement() {
        assertThrows(IllegalStateException.class, () -> new CaseMemoryEntity(
                "memory", SCOPE, "PERSON", "SELF", "NAME", "{\"value\":\"A\"}",
                MemoryLifecycle.SUPERSEDED, VerificationStatus.UNVERIFIED, MemorySourceType.USER_MESSAGE,
                "source", null, null, null, null, 1, NOW));
    }

    @Test
    void invalidTimeRangeIsRejected() {
        assertThrows(IllegalStateException.class, () -> new CaseMemoryEntity(
                "memory", SCOPE, "PERSON", "SELF", "NAME", "{\"value\":\"A\"}",
                MemoryLifecycle.ACTIVE, VerificationStatus.UNVERIFIED, MemorySourceType.USER_MESSAGE,
                "source", NOW, NOW.minusSeconds(1), null, null, 1, NOW));
    }
}
