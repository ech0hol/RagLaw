package com.raglaw.rag.contract;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContractClassifierTest {

    private final ContractClassifier classifier = new ContractClassifier();

    @Test
    void classifiesCivilContractByKeywords() {
        var result = classifier.classify("本借款合同约定违约金及担保责任。");
        assertThat(result.domain()).isEqualTo("CIVIL");
        assertThat(result.suggestedAgentCode()).isEqualTo("CONTRACT_CIVIL");
    }

    @Test
    void fallsBackToGeneral() {
        var result = classifier.classify("双方就合作事宜达成一致。");
        assertThat(result.domain()).isEqualTo("GENERAL");
        assertThat(result.suggestedAgentCode()).isEqualTo("CONTRACT_GENERAL");
    }
}
