package com.raglaw.agentadmin.dto;

import com.raglaw.agentadmin.domain.AgentPublishStatus;

public record TransitionAgentVersionRequest(AgentPublishStatus targetStatus) {}
