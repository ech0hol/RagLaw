package com.raglaw.memory.service;

import com.raglaw.memory.casefile.CaseScope;

public interface MemorySnapshotService {
    CaseMemorySnapshot freeze(CaseScope scope);
}
