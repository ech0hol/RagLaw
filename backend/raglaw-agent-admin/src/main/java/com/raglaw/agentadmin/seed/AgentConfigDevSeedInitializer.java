package com.raglaw.agentadmin.seed;

import com.raglaw.agentadmin.domain.AgentConfigEntity;
import com.raglaw.agentadmin.domain.AgentConfigRepository;
import com.raglaw.agentadmin.service.AgentConfigService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
@Order(0)
public class AgentConfigDevSeedInitializer implements ApplicationRunner {

    private static final String WEB_TOOLS = "[\"rag_search\",\"tavily-search\"]";
    private static final String EXPERT_TOOLS = "[\"rag_search\",\"tavily-search\"]";
    private static final String MCP_TAVILY = "[\"tavily\"]";

    private final AgentConfigRepository repository;
    private final AgentConfigService agentConfigService;

    public AgentConfigDevSeedInitializer(
            AgentConfigRepository repository,
            AgentConfigService agentConfigService
    ) {
        this.repository = repository;
        this.agentConfigService = agentConfigService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        saveAgent("agent_general", "GENERAL", "通用法律助手", "GENERAL", "dashscope:qwen-plus",
                "[]", "[]", "[\"STATUTE\",\"CASE\",\"CONTRACT\"]",
                "你是通用法律助手，负责理解用户问题并协调法规、案例、合同专家助手回答。若专家检索已返回可引用条文，禁止在回答中写「未检索到」；仅当检索 0 条时才说明局限。",
                WEB_TOOLS);
        saveAgent("agent_statute", "STATUTE", "法规助手", "STATUTE", "dashscope:qwen-plus",
                "[]",
                "[\"STATUTE_CONSTITUTIONAL\",\"STATUTE_CIVIL\",\"STATUTE_ADMIN\",\"STATUTE_ECONOMIC\",\"STATUTE_SOCIAL\",\"STATUTE_CRIMINAL\",\"STATUTE_PROCEDURE\",\"STATUTE_ECO_ENV\"]",
                "[]",
                "你是法规专家，覆盖宪法、民法商法、行政法、经济法、社会法、刑法、诉讼与非诉讼程序法、生态环境法等领域。若 rag_search 已返回可引用条文，禁止在回答中写「未检索到」；仅当检索 0 条时才说明局限。",
                EXPERT_TOOLS);
        saveAgent("agent_case", "CASE", "案例助手", "CASE", "dashscope:qwen-plus",
                "[]", "[\"CASE_CIVIL\"]", "[]",
                "你是案例检索专家，擅长从裁判文书中提炼要点。", EXPERT_TOOLS);
        saveAgent("agent_contract", "CONTRACT", "合同助手", "CONTRACT", "dashscope:qwen-max",
                "[\"risk-dimension-review\"]", "[\"STATUTE_CIVIL\",\"CASE_CIVIL\"]", "[]",
                "你是合同审查专家，识别风险并给出修订建议。", EXPERT_TOOLS);
        agentConfigService.reload();
    }

    private void saveAgent(
            String id,
            String code,
            String name,
            String type,
            String model,
            String skillsJson,
            String knowledgeScopesJson,
            String a2aPeersJson,
            String systemPrompt,
            String toolsJson
    ) {
        AgentConfigEntity entity = new AgentConfigEntity(
                id, code, name, type, true, model,
                skillsJson, knowledgeScopesJson, a2aPeersJson, systemPrompt, toolsJson
        );
        entity.setMcpServersJson(MCP_TAVILY);
        repository.save(entity);
    }
}
