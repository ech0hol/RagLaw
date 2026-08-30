package com.raglaw.rag.tool;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CatalogQueryDetectorTest {

    @Test
    void detectsInventoryQuestions() {
        assertTrue(CatalogQueryDetector.isCatalogQuery("当前有哪些法规可以查询"));
        assertTrue(CatalogQueryDetector.isCatalogQuery("知识库范围有哪些"));
        assertTrue(CatalogQueryDetector.isCatalogQuery("能查什么案例"));
    }

    @Test
    void rejectsSubstantiveLegalQuestions() {
        assertFalse(CatalogQueryDetector.isCatalogQuery("劳动合同解除有哪些法定情形"));
        assertFalse(CatalogQueryDetector.isCatalogQuery("民法典里有哪些合同条款"));
        assertFalse(CatalogQueryDetector.isCatalogQuery("拖欠工资如何维权"));
        assertFalse(CatalogQueryDetector.isCatalogQuery("刑法中有哪些刑罚"));
        assertFalse(CatalogQueryDetector.isCatalogQuery("刑法规定的刑罚有哪些"));
    }
}
