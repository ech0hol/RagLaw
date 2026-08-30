package com.raglaw.rag.retrieval;

import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.dto.RetrievalHit;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.retrieval.chunk.ChunkHeadingHeuristics;
import com.raglaw.rag.retrieval.chunk.ChunkHierarchyResolver;
import com.raglaw.rag.tool.RagSearchHit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class RetrievalContextEnricher {

    private final ChunkHierarchyResolver chunkHierarchyResolver;
    private final DocumentRepository documentRepository;
    private final RagProperties ragProperties;

    public RetrievalContextEnricher(
            ChunkHierarchyResolver chunkHierarchyResolver,
            DocumentRepository documentRepository,
            RagProperties ragProperties
    ) {
        this.chunkHierarchyResolver = chunkHierarchyResolver;
        this.documentRepository = documentRepository;
        this.ragProperties = ragProperties;
    }

    public RagSearchHit toSearchHit(RetrievalHit hit) {
        String path = hit.l3Path() != null && !hit.l3Path().isBlank() ? hit.l3Path() : hit.l2Path();
        ChunkHierarchyResolver.ResolvedChunk resolved = chunkHierarchyResolver.resolve(hit.chunkId());
        String displaySource = resolved.displayContent().isBlank() ? hit.content() : resolved.displayContent();
        if (ChunkHeadingHeuristics.looksLikeShortHeading(displaySource)
                && ChunkHeadingHeuristics.containsSubstantiveArticleBody(hit.content())) {
            displaySource = hit.content();
        }
        String displayExcerpt = truncate(displaySource, ragProperties.getRetrieval().getDisplayExcerptMaxChars());
        String llmSource = resolved.llmContent().isBlank() ? hit.content() : resolved.llmContent();
        String llmContent = truncate(llmSource, ragProperties.getRetrieval().getLlmContextMaxChars());
        String title = resolveTitle(hit.documentId());
        String referenceChunkId = resolved.referenceChunkId() != null ? resolved.referenceChunkId() : hit.chunkId();
        return new RagSearchHit(
                referenceChunkId,
                hit.documentId(),
                hit.score(),
                path,
                displayExcerpt,
                llmContent,
                title,
                resolved.matchedChunkId()
        );
    }

    private String resolveTitle(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            return null;
        }
        return documentRepository.findById(documentId)
                .map(DocumentEntity::getTitle)
                .filter(title -> title != null && !title.isBlank())
                .orElse(null);
    }

    public Map<String, String> preloadTitles(List<RetrievalHit> hits) {
        Set<String> documentIds = hits.stream()
                .map(RetrievalHit::documentId)
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .collect(Collectors.toSet());
        Map<String, String> titles = new HashMap<>();
        for (String documentId : documentIds) {
            String title = resolveTitle(documentId);
            if (title != null) {
                titles.put(documentId, title);
            }
        }
        return titles;
    }

    private static String truncate(String content, int maxChars) {
        if (content == null) {
            return "";
        }
        if (maxChars <= 0 || content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars) + "…";
    }
}
