package com.raglaw.agentscope.workflow;

public record WorkflowEvidenceReference(String documentId, String chunkId, Integer page, String clause, String checksum) {}
