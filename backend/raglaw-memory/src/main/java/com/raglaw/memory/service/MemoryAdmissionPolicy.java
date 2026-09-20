package com.raglaw.memory.service;

import com.raglaw.memory.domain.MemorySourceType;
import org.springframework.stereotype.Component;

/** Deterministic gate; an LLM may propose a candidate but cannot bypass it. */
@Component
public class MemoryAdmissionPolicy {
    public AdmissionDecision evaluate(MemoryCandidate candidate) {
        if (candidate == null) return AdmissionDecision.reject("candidate_missing");
        if (blank(candidate.subjectType()) || blank(candidate.subjectId()) || blank(candidate.predicate())) {
            return AdmissionDecision.reject("identity_missing");
        }
        if (blank(candidate.valueJson())) return AdmissionDecision.reject("value_missing");
        if (blank(candidate.sourceId())) return AdmissionDecision.reject("source_missing");
        if (candidate.sourceType() == null) return AdmissionDecision.reject("source_type_missing");
        if (candidate.sourceType() == MemorySourceType.AGENT_INFERENCE) {
            return AdmissionDecision.reject("agent_inference_requires_human_or_source");
        }
        if (isGreeting(candidate.predicate()) || isRawToolOutput(candidate)) {
            return AdmissionDecision.reject("not_reusable_case_fact");
        }
        if ("STATUTE".equalsIgnoreCase(candidate.subjectType())
                && (candidate.predicate().toUpperCase().contains("RAW")
                || candidate.predicate().toUpperCase().contains("WHOLE"))) {
            return AdmissionDecision.reject("whole_statute_not_case_memory");
        }
        if (candidate.validFrom() != null && candidate.validTo() != null
                && candidate.validTo().isBefore(candidate.validFrom())) {
            return AdmissionDecision.reject("invalid_time_range");
        }
        return AdmissionDecision.accept("reusable_sourced_fact");
    }

    private static boolean isGreeting(String predicate) {
        String normalized = predicate.toLowerCase();
        return normalized.equals("greeting") || normalized.equals("small_talk") || normalized.equals("ack");
    }

    private static boolean isRawToolOutput(MemoryCandidate candidate) {
        return candidate.sourceType() == MemorySourceType.AGENT_INFERENCE
                || candidate.predicate().toUpperCase().contains("RAW_TOOL");
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
