package com.raglaw.agentscope.workflow;

import com.raglaw.agentadmin.domain.AgentPublishStatus;
import com.raglaw.agentadmin.model.AgentVersionSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class RoleResolver {
    public ResolvedAgent resolve(RoleRequirement requirement, AgentResolutionContext context) {
        List<ResolvedAgent> eligible = context.candidates().stream()
                .filter(candidate -> eligible(candidate, requirement, context))
                .map(candidate -> score(candidate, requirement, context))
                .sorted(Comparator.comparingDouble(ResolvedAgent::score).reversed()
                        .thenComparing(ResolvedAgent::agentCode)
                        .thenComparing(ResolvedAgent::agentVersion, Comparator.reverseOrder()))
                .toList();
        if (eligible.isEmpty()) throw new NoEligibleAgentException("No eligible agent for role " + requirement.roleCode());
        return eligible.get(0);
    }

    private static boolean eligible(AgentVersionSnapshot candidate, RoleRequirement requirement, AgentResolutionContext context) {
        if (candidate.status() != AgentPublishStatus.PUBLISHED) return false;
        if (!candidate.manifest().supportedTaskTypes().contains(context.taskType())) return false;
        if (!candidate.manifest().supportedRiskLevels().contains(context.riskLevel())) return false;
        if (!candidate.manifest().capabilities().containsAll(requirement.capabilities())) return false;
        if (!candidate.manifest().domains().containsAll(requirement.domains())) return false;
        if (!context.availableInputs().containsAll(candidate.manifest().requiredInputs())) return false;
        Set<String> effective = intersection(candidate.toolPolicy().toolNames(), requirement.requiredTools(), context.userAllowedTools(), context.riskAllowedTools());
        return effective.containsAll(requirement.requiredTools()) && candidate.evaluationScore() >= requirement.minimumEvaluationScore();
    }

    private static ResolvedAgent score(AgentVersionSnapshot candidate, RoleRequirement requirement, AgentResolutionContext context) {
        double capability = requirement.capabilities().isEmpty() ? 1 : ratio(candidate.manifest().capabilities(), requirement.capabilities());
        double domain = requirement.domains().isEmpty() ? 1 : ratio(candidate.manifest().domains(), requirement.domains());
        double score = capability * 0.45 + domain * 0.25 + candidate.evaluationScore() * 0.2
                + (candidate.agentCode().equals(requirement.defaultAgentCode()) ? 0.1 : 0);
        List<String> reasons = new ArrayList<>();
        reasons.add("published"); reasons.add("capability_coverage=" + capability); reasons.add("domain_coverage=" + domain);
        if (candidate.agentCode().equals(requirement.defaultAgentCode())) reasons.add("default_agent_match");
        return new ResolvedAgent(requirement.roleCode(), candidate.agentCode(), candidate.version(),
                intersection(candidate.toolPolicy().toolNames(), requirement.requiredTools(), context.userAllowedTools(), context.riskAllowedTools()),
                candidate.configChecksum(), score, reasons);
    }

    private static double ratio(Set<String> actual, Set<String> required) { return required.isEmpty() ? 1 : required.stream().filter(actual::contains).count() / (double) required.size(); }

    private static Set<String> intersection(Set<String>... sets) {
        Set<String> result = sets.length == 0 || sets[0] == null ? new java.util.HashSet<>() : new java.util.HashSet<>(sets[0]);
        for (int i = 1; i < sets.length; i++) if (sets[i] != null) result.retainAll(sets[i]); else result.clear();
        return Set.copyOf(result);
    }
}
