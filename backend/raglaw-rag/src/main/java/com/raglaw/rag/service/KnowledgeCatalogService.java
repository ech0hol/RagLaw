package com.raglaw.rag.service;

import com.raglaw.rag.domain.CategoryEntity;
import com.raglaw.rag.domain.DocStatus;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.repository.CategoryRepository;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.tool.RagSearchHit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeCatalogService {

    static final int MAX_CATALOG_HITS = 30;
    static final int MAX_PER_DOC_TYPE = 20;

    private final DocumentRepository documentRepository;
    private final CategoryRepository categoryRepository;

    public KnowledgeCatalogService(
            DocumentRepository documentRepository,
            CategoryRepository categoryRepository
    ) {
        this.documentRepository = documentRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<RagSearchHit> buildCatalogHits(List<String> scopePaths, int limit) {
        int maxHits = Math.min(Math.max(limit, 1), MAX_CATALOG_HITS);
        long statuteCount = documentRepository.countByDocTypeAndStatus("STATUTE", DocStatus.INDEXED);
        long caseCount = documentRepository.countByDocTypeAndStatus("CASE", DocStatus.INDEXED);

        Map<String, Long> categoryCounts = categoryBreakdown(scopePaths);
        List<CatalogDocument> documents = listIndexedDocuments(scopePaths, maxHits);

        List<RagSearchHit> hits = new ArrayList<>();
        String summary = """
                知识库概览：已入库法规 %d 部、案例 %d 份。
                分类统计：%s
                以下列出可查询文档（仅可引用此清单中的名称，禁止补充未入库法规）：
                """.formatted(
                statuteCount,
                caseCount,
                formatCategoryCounts(categoryCounts)
        );
        hits.add(new RagSearchHit(
                "catalog-summary",
                null,
                1.0,
                "/KNOWLEDGE",
                summary,
                summary,
                "知识库概览",
                "catalog-summary"
        ));

        for (int i = 0; i < documents.size(); i++) {
            CatalogDocument doc = documents.get(i);
            String excerpt = "类型: %s | 分类: %s".formatted(docTypeLabel(doc.docType()), doc.categoryPath());
            hits.add(new RagSearchHit(
                    "catalog-" + doc.id(),
                    doc.id(),
                    1.0 - (i * 0.001),
                    doc.categoryPath(),
                    excerpt,
                    excerpt,
                    doc.title(),
                    "catalog-" + doc.id()
            ));
        }
        return hits;
    }

    private Map<String, Long> categoryBreakdown(List<String> scopePaths) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : documentRepository.countDocumentsByCategory()) {
            String path = (String) row[1];
            long count = ((Number) row[2]).longValue();
            if (count <= 0 || !matchesScope(path, scopePaths)) {
                continue;
            }
            String bucket = categoryBucket(path);
            counts.merge(bucket, count, Long::sum);
        }
        return counts;
    }

    private List<CatalogDocument> listIndexedDocuments(List<String> scopePaths, int limit) {
        List<CatalogDocument> all = new ArrayList<>();
        all.addAll(toCatalogDocuments(
                documentRepository.findByDocTypeAndStatus("STATUTE", DocStatus.INDEXED),
                scopePaths
        ));
        all.addAll(toCatalogDocuments(
                documentRepository.findByDocTypeAndStatus("CASE", DocStatus.INDEXED),
                scopePaths
        ));
        return all.stream()
                .sorted(Comparator.comparing(CatalogDocument::docType)
                        .thenComparing(CatalogDocument::categoryPath)
                        .thenComparing(CatalogDocument::title))
                .limit(limit)
                .toList();
    }

    private List<CatalogDocument> toCatalogDocuments(List<DocumentEntity> documents, List<String> scopePaths) {
        List<CatalogDocument> result = new ArrayList<>();
        for (DocumentEntity document : documents) {
            CategoryEntity category = categoryRepository.findById(document.getCategoryId()).orElse(null);
            String path = category == null ? "" : category.getPath();
            if (!matchesScope(path, scopePaths)) {
                continue;
            }
            result.add(new CatalogDocument(document.getId(), document.getTitle(), document.getDocType(), path));
        }
        return result;
    }

    private static boolean matchesScope(String categoryPath, List<String> scopePaths) {
        if (scopePaths == null || scopePaths.isEmpty()) {
            return true;
        }
        if (categoryPath == null || categoryPath.isBlank()) {
            return false;
        }
        for (String scope : scopePaths) {
            if (scope == null || scope.isBlank()) {
                continue;
            }
            if (categoryPath.equals(scope) || categoryPath.startsWith(scope.endsWith("/") ? scope : scope + "/")) {
                return true;
            }
        }
        return false;
    }

    private static String categoryBucket(String path) {
        if (path == null || path.isBlank()) {
            return "未分类";
        }
        String[] parts = path.split("/");
        if (parts.length >= 2) {
            return "/" + parts[1];
        }
        return path;
    }

    private static String formatCategoryCounts(Map<String, Long> categoryCounts) {
        if (categoryCounts.isEmpty()) {
            return "暂无分类数据";
        }
        return categoryCounts.entrySet().stream()
                .map(entry -> entry.getKey() + " " + entry.getValue() + " 部")
                .collect(Collectors.joining("；"));
    }

    private static String docTypeLabel(String docType) {
        if ("CASE".equals(docType)) {
            return "案例";
        }
        if ("STATUTE".equals(docType)) {
            return "法规";
        }
        return docType;
    }

    private record CatalogDocument(String id, String title, String docType, String categoryPath) {
    }
}
