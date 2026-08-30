package com.raglaw.rag.tool;

import java.util.regex.Pattern;

/**
 * Detects meta-inventory questions (e.g. "what laws can I query") that should use
 * catalog mode inside rag_search instead of semantic chunk retrieval.
 */
public final class CatalogQueryDetector {

    private static final Pattern CATALOG_QUERY = Pattern.compile(
            "(知识库|入库|收录|可查询|能查询|可查什么|能查什么|查什么|查询范围)"
                    + "|当前有哪些.*(法规|案例)"
                    + "|(法规|案例|法律|文档).*(可查询|能查询|查询范围)"
                    + "|(有哪些|有什么).*(法规|案例).*(可以|能).*(查|查询)"
                    + "|(可查|能查|可以查).*(法规|案例|法律|文档)"
    );

    private static final Pattern SUBSTANTIVE_QUERY = Pattern.compile(
            "(第[零一二三四五六七八九十百\\d]+条|条款|规定|情形|责任|效力|如何|怎么|是否|能否|构成|适用|"
                    + "刑罚|主刑|附加刑|罪名|种类|处罚|权利|义务|概念|定义|含义|包括)"
    );

    private CatalogQueryDetector() {
    }

    public static boolean isCatalogQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String normalized = query.trim();
        if (!CATALOG_QUERY.matcher(normalized).find()) {
            return false;
        }
        return !SUBSTANTIVE_QUERY.matcher(normalized).find();
    }
}
