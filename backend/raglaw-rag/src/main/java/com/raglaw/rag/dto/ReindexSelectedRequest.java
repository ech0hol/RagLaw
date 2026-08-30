package com.raglaw.rag.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReindexSelectedRequest(
        @NotEmpty @Size(max = 50) List<String> documentIds
) {
}
