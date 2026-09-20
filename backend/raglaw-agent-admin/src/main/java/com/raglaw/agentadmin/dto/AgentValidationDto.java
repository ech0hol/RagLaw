package com.raglaw.agentadmin.dto;

import com.raglaw.agentadmin.domain.AgentPublishStatus;
import java.util.List;

public record AgentValidationDto(
        String agentCode,
        int version,
        boolean valid,
        List<String> errors,
        AgentPublishStatus status
) {
    public AgentValidationDto {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
