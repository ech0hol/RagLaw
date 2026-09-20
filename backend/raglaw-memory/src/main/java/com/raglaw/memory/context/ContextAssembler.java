package com.raglaw.memory.context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service("governedContextAssembler")
public class ContextAssembler {
    private final ContextBudgetPolicy budgetPolicy;
    public ContextAssembler(ContextBudgetPolicy budgetPolicy) { this.budgetPolicy = budgetPolicy; }

    public AssembledContext assemble(ContextRequest request) {
        ContextBudget budget = budgetPolicy.budgetFor(request.modelWindow(), request.profile());
        List<ContextItem> items = request.items().stream().sorted(Comparator
                .comparing((ContextItem item) -> request.profile().priority(item.type()).ordinal())
                .thenComparing(item -> profileOrder(request.profile(), item.type()))
                .thenComparing(ContextItem::id)).toList();
        int p0Tokens = items.stream().filter(item -> request.profile().priority(item.type()) == ContextPriority.P0_REQUIRED).mapToInt(ContextItem::estimatedTokens).sum();
        if (p0Tokens > budget.inputLimit()) throw new CriticalContextOverflowException("required context exceeds input budget");
        List<ContextItem> included = new ArrayList<>(); List<String> omitted = new ArrayList<>(); int used = 0; Set<String> seen = new HashSet<>();
        for (ContextItem item : items) {
            if (!seen.add(item.id()) || used + item.estimatedTokens() > budget.inputLimit()) { omitted.add(item.id()); continue; }
            included.add(item); used += item.estimatedTokens();
        }
        return new AssembledContext(request.profileCode(), request.memorySnapshotVersion(), included, omitted, used, budget.inputLimit(), budget.outputReserve());
    }
    private static int profileOrder(NodeContextProfile profile, ContextSectionType type) { int index = profile.sectionOrder().indexOf(type); return index < 0 ? Integer.MAX_VALUE : index; }
}
