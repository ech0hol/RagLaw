package com.raglaw.rag.service;

public final class KnowledgePathJoiner {

    private KnowledgePathJoiner() {
    }

    public static String joinPath(String l1, String l2, String l3) {
        if (l3 != null && !l3.isBlank()) {
            return l3;
        }
        if (l2 != null && !l2.isBlank()) {
            return l2;
        }
        return l1 != null ? l1 : "";
    }
}
