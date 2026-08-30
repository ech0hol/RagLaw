package com.raglaw.rag.contract;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ContractKnowledgeScopeMapper {

    private static final List<String> CONTRACT_SCOPES = List.of(
            "STATUTE_CIVIL",
            "STATUTE_SOCIAL",
            "CASE_CIVIL"
    );

    public List<String> scopesForAgent(String suggestedAgentCode) {
        return CONTRACT_SCOPES;
    }
}
