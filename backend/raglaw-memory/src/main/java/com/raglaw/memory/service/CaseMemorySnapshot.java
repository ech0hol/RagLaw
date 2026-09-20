package com.raglaw.memory.service;

import com.raglaw.memory.casefile.CaseScope;
import java.time.Instant;

public record CaseMemorySnapshot(CaseScope scope, long version, Instant createdAt) {
}
