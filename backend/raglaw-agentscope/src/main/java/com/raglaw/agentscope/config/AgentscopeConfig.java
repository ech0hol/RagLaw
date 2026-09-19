package com.raglaw.agentscope.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import com.raglaw.agentscope.routing.WorkflowCatalog;

@Configuration
@EnableConfigurationProperties({AgentscopeLlmProperties.class, AgentscopeMcpProperties.class})
public class AgentscopeConfig {

    @Bean
    public WorkflowCatalog workflowCatalog() {
        return WorkflowCatalog.standard();
    }
}
