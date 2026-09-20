package com.raglaw.agentadmin.dto;

public record AgentEvaluationSummaryDto(
        String agentCode,
        int version,
        double score,
        String configChecksum,
        String status
) {}
