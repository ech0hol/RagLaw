package com.raglaw.memory.service;

import com.raglaw.memory.domain.CaseMemoryEntity;
import java.util.List;

public interface CaseMemoryQueryService {
    List<CaseMemoryEntity> findForContext(ContextRequest request);
}
