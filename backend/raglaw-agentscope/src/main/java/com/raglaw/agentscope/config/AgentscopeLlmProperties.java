package com.raglaw.agentscope.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "raglaw.llm")
public class AgentscopeLlmProperties {

    private boolean mock = false;

    public boolean isMock() {
        return mock;
    }

    public void setMock(boolean mock) {
        this.mock = mock;
    }
}
