package com.raglaw.agentscope.trace;

import com.raglaw.agentscope.domain.RagTraceEntity;
import com.raglaw.agentscope.domain.RagTraceRepository;
import com.raglaw.agentscope.domain.ShadowRouteLogRepository;
import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TraceDeletionService {

    private static final int MAX_BATCH_SIZE = 50;

    private final RagTraceRepository traceRepository;
    private final ShadowRouteLogRepository shadowRouteLogRepository;

    public TraceDeletionService(
            RagTraceRepository traceRepository,
            ShadowRouteLogRepository shadowRouteLogRepository
    ) {
        this.traceRepository = traceRepository;
        this.shadowRouteLogRepository = shadowRouteLogRepository;
    }

    @Transactional
    public void delete(String traceId) {
        if (!traceRepository.existsById(traceId)) {
            throw new BusinessException(ErrorCodes.NOT_FOUND, "Trace 不存在");
        }
        shadowRouteLogRepository.deleteByTraceId(traceId);
        traceRepository.deleteById(traceId);
    }

    @Transactional
    public int deleteBatch(List<String> traceIds) {
        if (traceIds == null || traceIds.isEmpty()) {
            throw new BusinessException(ErrorCodes.VALIDATION, "traceIds 不能为空");
        }
        Set<String> uniqueIds = new LinkedHashSet<>(traceIds);
        if (uniqueIds.size() > MAX_BATCH_SIZE) {
            throw new BusinessException(ErrorCodes.VALIDATION, "单次最多删除 " + MAX_BATCH_SIZE + " 条 trace");
        }
        List<String> ids = new ArrayList<>(uniqueIds);
        List<RagTraceEntity> found = traceRepository.findAllById(ids);
        if (found.isEmpty()) {
            return 0;
        }
        List<String> foundIds = found.stream().map(RagTraceEntity::getId).toList();
        shadowRouteLogRepository.deleteByTraceIdIn(foundIds);
        traceRepository.deleteAll(found);
        return found.size();
    }
}
