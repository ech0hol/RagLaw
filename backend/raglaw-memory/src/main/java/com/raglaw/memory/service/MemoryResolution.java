package com.raglaw.memory.service;

import com.raglaw.memory.domain.MemoryAction;
import com.raglaw.memory.domain.VerificationStatus;
import java.util.List;

public record MemoryResolution(
        MemoryAction action,
        List<String> affectedMemoryIds,
        VerificationStatus verificationStatus,
        String reason
) {
    public MemoryResolution {
        affectedMemoryIds = affectedMemoryIds == null ? List.of() : List.copyOf(affectedMemoryIds);
    }
}
