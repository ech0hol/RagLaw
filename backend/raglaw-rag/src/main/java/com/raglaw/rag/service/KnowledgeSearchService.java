package com.raglaw.rag.service;

import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.dto.KnowledgeHitDto;
import com.raglaw.rag.dto.KnowledgeSearchPageDto;
import com.raglaw.rag.dto.RetrievalHit;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.retrieval.HybridRetriever;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeSearchService {

    private final HybridRetriever hybridRetriever;
    private final DocumentRepository documentRepository;

    public KnowledgeSearchService(HybridRetriever hybridRetriever, DocumentRepository documentRepository) {
        this.hybridRetriever = hybridRetriever;
        this.documentRepository = documentRepository;
    }

    public List<KnowledgeHitDto> search(String query, String docType, int limit) {
        return searchPage(query, docType, null, 0, limit).items();
    }

    public KnowledgeSearchPageDto searchPage(
            String query,
            String docType,
            String l2Path,
            int page,
            int pageSize
    ) {
        if (query == null || query.isBlank()) {
            return new KnowledgeSearchPageDto(List.of(), page, pageSize, 0);
        }
        int fetchLimit = Math.max(pageSize, 1) * (page + 1) * 2;
        List<RetrievalHit> hits = hybridRetriever.search(query, List.of(), fetchLimit, null);
        List<KnowledgeHitDto> all = mapHits(hits, docType, l2Path);

        int from = Math.max(page, 0) * pageSize;
        int to = Math.min(from + pageSize, all.size());
        List<KnowledgeHitDto> pageItems = from >= all.size() ? List.of() : all.subList(from, to);
        return new KnowledgeSearchPageDto(pageItems, page, pageSize, all.size());
    }

    private List<KnowledgeHitDto> mapHits(List<RetrievalHit> hits, String docType, String l2Path) {
        List<RetrievalHit> filtered = new ArrayList<>();
        for (RetrievalHit hit : hits) {
            if (docType != null && !docType.isBlank()
                    && (hit.l1Path() == null || !hit.l1Path().contains("/" + docType))) {
                continue;
            }
            if (l2Path != null && !l2Path.isBlank()
                    && (hit.l2Path() == null || !hit.l2Path().startsWith(l2Path))) {
                continue;
            }
            filtered.add(hit);
        }

        List<String> documentIds = filtered.stream().map(RetrievalHit::documentId).distinct().toList();
        Map<String, DocumentEntity> documents = documentRepository.findAllById(documentIds).stream()
                .collect(Collectors.toMap(DocumentEntity::getId, Function.identity()));

        return filtered.stream()
                .map(hit -> {
                    DocumentEntity doc = documents.get(hit.documentId());
                    String path = joinPath(hit.l1Path(), hit.l2Path(), hit.l3Path());
                    return new KnowledgeHitDto(
                            hit.chunkId(),
                            hit.documentId(),
                            doc != null ? doc.getTitle() : "未知文档",
                            path,
                            excerpt(hit.content()),
                            hit.score()
                    );
                })
                .toList();
    }

    private static String joinPath(String l1, String l2, String l3) {
        StringBuilder sb = new StringBuilder();
        if (l1 != null) {
            sb.append(l1);
        }
        if (l2 != null) {
            sb.append(l2);
        }
        if (l3 != null) {
            sb.append(l3);
        }
        return sb.toString();
    }

    private static String excerpt(String content) {
        if (content == null) {
            return "";
        }
        return content.length() > 200 ? content.substring(0, 200) + "…" : content;
    }
}
