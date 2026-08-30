package com.raglaw.rag.contract;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ContractClassifierTest {

    private final ContractClassifier classifier = new ContractClassifier();

    @Test
    void classify_alwaysReturnsContractAgent() {
        ContractClassifier.ClassificationResult result = classifier.classify("刑事合同 租赁 借款");
        assertThat(result.suggestedAgentCode()).isEqualTo("CONTRACT");
    }

    @Test
    void classify_emptyText_returnsContract() {
        ContractClassifier.ClassificationResult result = classifier.classify("");
        assertThat(result.suggestedAgentCode()).isEqualTo("CONTRACT");
    }
}
