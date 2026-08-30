package com.raglaw.agentscope.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record TraceDeleteBatchRequest(
        @NotEmpty @Size(max = 50) List<String> traceIds
) {
}
