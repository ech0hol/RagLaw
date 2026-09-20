package com.raglaw.agentadmin.dto;

import com.raglaw.agentadmin.domain.AgentPublishStatus;
import java.time.Instant;

public record AgentVersionDto(
        String agentCode,
        int version,
        AgentPublishStatus status,
        double evaluationScore,
        String configChecksum,
        Instant createdAt,
        String createdBy,
        Instant publishedAt,
        String publishedBy
) {}
