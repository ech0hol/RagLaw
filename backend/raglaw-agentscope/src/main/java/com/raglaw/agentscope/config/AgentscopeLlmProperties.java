package com.raglaw.agentscope.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "raglaw.llm")
public class AgentscopeLlmProperties {

    private boolean mock = false;
    private String taskClassifierModel = "qwen-turbo";
    private String taskClassifierPromptVersion = "task-classifier-v1";

    public boolean isMock() {
        return mock;
    }

    public void setMock(boolean mock) {
        this.mock = mock;
    }

    public String getTaskClassifierModel() {
        return taskClassifierModel;
    }

    public void setTaskClassifierModel(String taskClassifierModel) {
        if (taskClassifierModel != null && !taskClassifierModel.isBlank()) {
            this.taskClassifierModel = taskClassifierModel;
        }
    }

    public String getTaskClassifierPromptVersion() {
        return taskClassifierPromptVersion;
    }

    public void setTaskClassifierPromptVersion(String taskClassifierPromptVersion) {
        if (taskClassifierPromptVersion != null && !taskClassifierPromptVersion.isBlank()) {
            this.taskClassifierPromptVersion = taskClassifierPromptVersion;
        }
    }
}
