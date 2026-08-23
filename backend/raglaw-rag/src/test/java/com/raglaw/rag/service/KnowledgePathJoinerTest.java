package com.raglaw.rag.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class KnowledgePathJoinerTest {

    @Test
    void prefersDeepestPathWithoutDuplication() {
        assertEquals(
                "/STATUTE/CIVIL/LABOR",
                KnowledgePathJoiner.joinPath("/STATUTE", "/STATUTE/CIVIL", "/STATUTE/CIVIL/LABOR")
        );
    }

    @Test
    void fallsBackToL2WhenL3Missing() {
        assertEquals("/STATUTE/CIVIL", KnowledgePathJoiner.joinPath("/STATUTE", "/STATUTE/CIVIL", null));
    }
}
