package com.raglaw.agentscope.shadow;

import com.raglaw.agentscope.domain.ShadowRouteLogEntity;
import com.raglaw.agentscope.domain.ShadowRouteLogRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ShadowRouteQueryService {

    private final ShadowRouteLogRepository shadowRouteLogRepository;

    public ShadowRouteQueryService(ShadowRouteLogRepository shadowRouteLogRepository) {
        this.shadowRouteLogRepository = shadowRouteLogRepository;
    }

    public List<ShadowRouteLogDto> listByTrace(String traceId) {
        return shadowRouteLogRepository.findByTraceIdOrderByCreatedAtAsc(traceId).stream()
                .map(this::toDto)
                .toList();
    }

    public ShadowRouteSummaryDto summary(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        List<ShadowRouteLogEntity> logs = shadowRouteLogRepository.findByCreatedAtAfterOrderByCreatedAtDesc(since);
        int documentTotal = 0;
        int documentHits = 0;
        int a2aTotal = 0;
        int a2aAgreements = 0;
        for (ShadowRouteLogEntity log : logs) {
            if ("DOCUMENT".equals(log.getShadowType())) {
                documentTotal++;
                if (Boolean.TRUE.equals(log.getHit())) {
                    documentHits++;
                }
            } else if ("A2A".equals(log.getShadowType())) {
                a2aTotal++;
                if (Boolean.TRUE.equals(log.getHit())) {
                    a2aAgreements++;
                }
            }
        }
        return new ShadowRouteSummaryDto(
                days,
                documentTotal,
                documentHits,
                rate(documentHits, documentTotal),
                a2aTotal,
                a2aAgreements,
                rate(a2aAgreements, a2aTotal)
        );
    }

    private static double rate(int hits, int total) {
        if (total == 0) {
            return 0.0;
        }
        return (double) hits / total;
    }

    private ShadowRouteLogDto toDto(ShadowRouteLogEntity entity) {
        return new ShadowRouteLogDto(
                entity.getId(),
                entity.getTraceId(),
                entity.getShadowType(),
                entity.getUserValue(),
                entity.getSystemValue(),
                entity.getHit(),
                entity.getRank(),
                entity.getConfidenceJson(),
                entity.getCreatedAt()
        );
    }

    public record ShadowRouteLogDto(
            String id,
            String traceId,
            String shadowType,
            String userValue,
            String systemValue,
            Boolean hit,
            Integer rank,
            String confidenceJson,
            Instant createdAt
    ) {
    }

    public record ShadowRouteSummaryDto(
            int days,
            int documentTotal,
            int documentHits,
            double documentHitRate,
            int a2aTotal,
            int a2aAgreements,
            double a2aAgreementRate
    ) {
    }
}
