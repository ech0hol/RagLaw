package com.raglaw.rag.contract;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContractKnowledgeScopeMapperTest {

    private final ContractKnowledgeScopeMapper mapper = new ContractKnowledgeScopeMapper();

    @Test
    void scopesForAgent_returnsStatuteAndCaseCodes() {
        assertThat(mapper.scopesForAgent("CONTRACT"))
                .containsExactly(
                        "STATUTE_CIVIL",
                        "STATUTE_SOCIAL",
                        "CASE_CIVIL"
                );
    }

    @Test
    void scopesForAgent_ignoresLegacyCodes() {
        assertThat(mapper.scopesForAgent("CONTRACT_CIVIL"))
                .containsExactly(
                        "STATUTE_CIVIL",
                        "STATUTE_SOCIAL",
                        "CASE_CIVIL"
                );
    }
}
