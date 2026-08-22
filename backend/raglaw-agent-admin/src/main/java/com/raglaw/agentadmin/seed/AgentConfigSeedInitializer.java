package com.raglaw.agentadmin.seed;

import com.raglaw.agentadmin.domain.AgentConfigEntity;
import com.raglaw.agentadmin.domain.AgentConfigRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
@Order(0)
public class AgentConfigSeedInitializer implements ApplicationRunner {

    private final AgentConfigRepository repository;

    public AgentConfigSeedInitializer(AgentConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }

        repository.save(new AgentConfigEntity(
                "agent_general",
                "GENERAL",
                "通用法律助手",
                "GENERAL",
                true,
                "dashscope:qwen-plus",
                "[]",
                "[]",
                "[\"STATUTE_CIVIL\",\"CASE_CIVIL\",\"CONTRACT_GENERAL\"]",
                "你是通用法律助手，负责理解用户问题并协调专家助手回答。",
                "[]"
        ));

        repository.save(new AgentConfigEntity(
                "agent_statute_civil",
                "STATUTE_CIVIL",
                "民法商法规范助手",
                "STATUTE",
                true,
                "dashscope:qwen-plus",
                "[]",
                "[\"cat_l2_statute_civil\"]",
                "[]",
                "你是法规专家，专注民法商法领域，回答需引用依据。",
                "[\"rag_search\"]"
        ));

        repository.save(new AgentConfigEntity(
                "agent_contract_general",
                "CONTRACT_GENERAL",
                "合同审查通用助手",
                "CONTRACT",
                true,
                "dashscope:qwen-max",
                "[\"risk-dimension-review\"]",
                "[\"cat_l2_contract_civil\"]",
                "[]",
                "你是合同审查专家，识别风险并给出修订建议。",
                "[\"rag_search\"]"
        ));
    }
}
