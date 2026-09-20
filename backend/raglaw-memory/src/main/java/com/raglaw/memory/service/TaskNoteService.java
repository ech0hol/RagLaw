package com.raglaw.memory.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class TaskNoteService {

    /**
     * Merges a node delta into the frozen task note. The caller supplies the fact IDs visible
     * in that snapshot; this prevents a late node from smuggling a newer fact into an old note.
     */
    public TaskNote merge(TaskNote current, TaskNote delta,
                          Set<String> factIdsVisibleAtDeltaSnapshot,
                          Set<String> resolvedQuestions) {
        if (current == null) throw new IllegalArgumentException("current");
        if (delta == null) throw new IllegalArgumentException("delta");
        Set<String> visible = factIdsVisibleAtDeltaSnapshot == null ? Set.of() : factIdsVisibleAtDeltaSnapshot;
        if (!visible.containsAll(delta.confirmedFactIds())) {
            throw new IllegalArgumentException("note contains facts newer than its snapshot");
        }
        if (delta.snapshotVersion() < current.snapshotVersion()) {
            throw new IllegalArgumentException("note snapshot cannot move backwards");
        }

        Set<String> questions = new LinkedHashSet<>(current.unresolvedQuestions());
        questions.addAll(delta.unresolvedQuestions());
        if (resolvedQuestions != null) questions.removeAll(resolvedQuestions);
        return new TaskNote(
                delta.objective().isBlank() ? current.objective() : delta.objective(),
                union(current.confirmedFactIds(), delta.confirmedFactIds()),
                union(current.decisionIds(), delta.decisionIds()),
                new ArrayList<>(questions),
                union(current.sourceIds(), delta.sourceIds()),
                delta.snapshotVersion()
        );
    }

    private static List<String> union(List<String> left, List<String> right) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (left != null) values.addAll(left);
        if (right != null) values.addAll(right);
        return List.copyOf(values);
    }
}
