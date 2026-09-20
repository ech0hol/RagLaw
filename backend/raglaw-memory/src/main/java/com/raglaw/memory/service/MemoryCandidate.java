package com.raglaw.memory.service;

import com.raglaw.memory.domain.MemorySourceType;
import java.time.Instant;

public record MemoryCandidate(
        String subjectType,
        String subjectId,
        String predicate,
        String valueJson,
        Instant validFrom,
        Instant validTo,
        MemorySourceType sourceType,
        String sourceId,
        boolean explicitCorrection,
        boolean negated
) {
}
