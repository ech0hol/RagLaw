package com.raglaw.agentadmin.dto;

public record PublishAgentRequest(int version, String evaluationVersion) {
    public PublishAgentRequest {
        if (version <= 0) throw new IllegalArgumentException("version");
        if (evaluationVersion == null || evaluationVersion.isBlank()) throw new IllegalArgumentException("evaluationVersion");
    }
}
