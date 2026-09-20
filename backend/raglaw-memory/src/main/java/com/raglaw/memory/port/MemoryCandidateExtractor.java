package com.raglaw.memory.port;

import com.raglaw.memory.casefile.CaseScope;
import com.raglaw.memory.service.MemoryCandidate;
import java.util.List;

public interface MemoryCandidateExtractor {
    List<MemoryCandidate> extract(CaseScope scope, String sourceMessageId, String messageText);
}
