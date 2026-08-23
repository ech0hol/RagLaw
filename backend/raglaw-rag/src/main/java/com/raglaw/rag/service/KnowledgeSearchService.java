package com.raglaw.rag.service;

import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.dto.KnowledgeHitDto;
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
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<RetrievalHit> hits = hybridRetriever.search(query, List.of(), Math.max(limit, 1) * 2, null);
        List<RetrievalHit> filtered = new ArrayList<>();
        for (RetrievalHit hit : hits) {
            if (docType == null || docType.isBlank()) {
                filtered.add(hit);
            } else if (hit.l1Path() != null && hit.l1Path().contains("/" + docType)) {
                filtered.add(hit);
            }
            if (filtered.size() >= limit) {
                break;
            }
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
