package com.raglaw.memory.service;

import com.raglaw.memory.domain.MemoryAction;

public record MemoryCommandResult(
        MemoryResolution resolution,
        String memoryId,
        long caseSequence,
        boolean admitted
) {
    public static MemoryCommandResult rejected(String reason) {
        return new MemoryCommandResult(
                new MemoryResolution(MemoryAction.NO_OP, java.util.List.of(),
                        com.raglaw.memory.domain.VerificationStatus.UNVERIFIED, reason),
                null, 0, false);
    }
}
