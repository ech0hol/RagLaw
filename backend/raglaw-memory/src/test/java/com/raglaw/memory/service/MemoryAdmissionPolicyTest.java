package com.raglaw.memory.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.raglaw.memory.domain.MemorySourceType;
import org.junit.jupiter.api.Test;

class MemoryAdmissionPolicyTest {
    private final MemoryAdmissionPolicy policy = new MemoryAdmissionPolicy();

    @Test
    void acceptsReusableSourcedFact() {
        assertThat(policy.evaluate(candidate("MONTHLY_SALARY", "{\"amount\":15000}"))).satisfies(result -> {
            assertThat(result.accepted()).isTrue();
        });
    }

    @Test
    void rejectsGreetingRawOutputAndUnsupportedInference() {
        assertThat(policy.evaluate(candidate("greeting", "hello")).accepted()).isFalse();
        assertThat(policy.evaluate(candidate("RAW_TOOL_OUTPUT", "{}" )).accepted()).isFalse();
        MemoryCandidate inference = new MemoryCandidate("PERSON", "SELF", "NAME", "{\"value\":\"A\"}",
                null, null, MemorySourceType.AGENT_INFERENCE, "agent-1", false, false);
        assertThat(policy.evaluate(inference).accepted()).isFalse();
    }

    @Test
    void rejectsSourceLessClaim() {
        MemoryCandidate missingSource = new MemoryCandidate("PERSON", "SELF", "NAME", "{\"value\":\"A\"}",
                null, null, MemorySourceType.USER_MESSAGE, "", false, false);
        assertThat(policy.evaluate(missingSource).accepted()).isFalse();
    }

    private static MemoryCandidate candidate(String predicate, String value) {
        return new MemoryCandidate("EMPLOYEE", "SELF", predicate, value, null, null,
                MemorySourceType.USER_MESSAGE, "message-1", false, false);
    }
}
