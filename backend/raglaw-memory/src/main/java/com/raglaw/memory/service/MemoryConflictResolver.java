package com.raglaw.memory.service;

import com.raglaw.memory.domain.CaseMemoryEntity;
import com.raglaw.memory.domain.MemoryAction;
import com.raglaw.memory.domain.MemorySourceType;
import com.raglaw.memory.domain.VerificationStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Resolves updates by a structured conflict slot. Embeddings are intentionally not used here.
 */
@Component
public class MemoryConflictResolver {

    public MemoryResolution resolve(MemoryCandidate candidate, List<CaseMemoryEntity> existing) {
        List<CaseMemoryEntity> samePredicate = existing == null ? List.of() : existing.stream()
                .filter(memory -> sameSlot(candidate, memory))
                .toList();

        for (CaseMemoryEntity memory : samePredicate) {
            if (normalize(memory.getValueJson()).equals(normalize(candidate.valueJson()))) {
                return new MemoryResolution(MemoryAction.NO_OP, List.of(memory.getId()),
                        memory.getVerification(), "identical_fact_add_source");
            }
        }

        if (candidate.explicitCorrection() && !samePredicate.isEmpty()) {
            return new MemoryResolution(MemoryAction.SUPERSEDE,
                    samePredicate.stream().map(CaseMemoryEntity::getId).toList(),
                    candidate.sourceType() == MemorySourceType.HUMAN_REVIEW
                            ? VerificationStatus.HUMAN_VERIFIED : VerificationStatus.USER_CONFIRMED,
                    "explicit_correction");
        }

        CaseMemoryEntity employmentStatus = findPredicate(existing, "EMPLOYMENT_STATUS");
        if ("EMPLOYMENT_END_DATE".equalsIgnoreCase(candidate.predicate()) && employmentStatus != null) {
            return new MemoryResolution(MemoryAction.INVALIDATE, List.of(employmentStatus.getId()),
                    VerificationStatus.CORROBORATED, "employment_end_invalidates_ongoing_status");
        }

        if (isEnrichment(candidate, existing)) {
            return new MemoryResolution(MemoryAction.ENRICH,
                    existing.stream().filter(memory -> sameSubject(candidate, memory))
                            .map(CaseMemoryEntity::getId).toList(),
                    VerificationStatus.CORROBORATED, "same_subject_additional_attribute");
        }

        if (!samePredicate.isEmpty() && hasIndependentSourceConflict(candidate, samePredicate)) {
            return new MemoryResolution(MemoryAction.MARK_DISPUTED,
                    samePredicate.stream().map(CaseMemoryEntity::getId).toList(),
                    VerificationStatus.DISPUTED, "independent_sources_disagree");
        }

        return new MemoryResolution(MemoryAction.ADD, new ArrayList<>(),
                VerificationStatus.UNVERIFIED, "new_sourced_fact");
    }

    private static boolean sameSlot(MemoryCandidate candidate, CaseMemoryEntity memory) {
        return sameSubject(candidate, memory)
                && candidate.predicate().equalsIgnoreCase(memory.getPredicate())
                && overlaps(candidate.validFrom(), candidate.validTo(), memory.getValidFrom(), memory.getValidTo());
    }

    private static boolean sameSubject(MemoryCandidate candidate, CaseMemoryEntity memory) {
        return candidate.subjectType().equalsIgnoreCase(memory.getSubjectType())
                && candidate.subjectId().equalsIgnoreCase(memory.getSubjectId());
    }

    private static boolean overlaps(java.time.Instant leftFrom, java.time.Instant leftTo,
                                   java.time.Instant rightFrom, java.time.Instant rightTo) {
        return (leftTo == null || rightFrom == null || !leftTo.isBefore(rightFrom))
                && (rightTo == null || leftFrom == null || !rightTo.isBefore(leftFrom));
    }

    private static boolean isEnrichment(MemoryCandidate candidate, List<CaseMemoryEntity> existing) {
        if (existing == null || existing.isEmpty()) return false;
        String predicate = candidate.predicate().toUpperCase(Locale.ROOT);
        if (!predicate.contains("BASIS") && !predicate.contains("TAX")) return false;
        return existing.stream().anyMatch(memory -> sameSubject(candidate, memory)
                && memory.getPredicate().toUpperCase(Locale.ROOT).contains("SALARY"));
    }

    private static CaseMemoryEntity findPredicate(List<CaseMemoryEntity> existing, String predicate) {
        if (existing == null) return null;
        return existing.stream().filter(memory -> predicate.equalsIgnoreCase(memory.getPredicate())).findFirst().orElse(null);
    }

    private static boolean hasIndependentSourceConflict(MemoryCandidate candidate, List<CaseMemoryEntity> memories) {
        return memories.stream().anyMatch(memory -> memory.getSourceType() != candidate.sourceType()
                && !normalize(memory.getValueJson()).equals(normalize(candidate.valueJson())));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
