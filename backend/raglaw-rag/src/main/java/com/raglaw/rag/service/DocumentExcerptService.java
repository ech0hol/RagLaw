package com.raglaw.rag.service;

import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.exception.BusinessException;
import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.dto.DocumentExcerptDto;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.retrieval.FullTextSnippetExtractor;
import com.raglaw.rag.retrieval.chunk.ChunkHeadingHeuristics;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentExcerptService {

    private final DocumentRepository documentRepository;
    private final DocumentFullTextService documentFullTextService;
    private final RagProperties ragProperties;

    public DocumentExcerptService(
            DocumentRepository documentRepository,
            DocumentFullTextService documentFullTextService,
            RagProperties ragProperties
    ) {
        this.documentRepository = documentRepository;
        this.documentFullTextService = documentFullTextService;
        this.ragProperties = ragProperties;
    }

    @Transactional(readOnly = true)
    public DocumentExcerptDto getExcerpt(String documentId, String anchorsParam) {
        documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "文档不存在"));
        List<String> anchors = parseAnchors(anchorsParam);
        if (anchors.isEmpty()) {
            throw new BusinessException(ErrorCodes.VALIDATION, "请提供至少一个法条锚点");
        }
        String fullText = documentFullTextService.buildFullText(documentId);
        if (fullText.isBlank()) {
            return new DocumentExcerptDto("");
        }
        int maxChars = ragProperties.getRetrieval().getDisplayExcerptMaxChars();
        String excerpt = FullTextSnippetExtractor.extractAroundArticles(fullText, anchors, maxChars);
        if (excerpt.isBlank()) {
            excerpt = FullTextSnippetExtractor.extractAroundAnchor(fullText, anchors.get(0), maxChars);
        }
        if (ChunkHeadingHeuristics.looksLikeTableOfContents(excerpt)) {
            return new DocumentExcerptDto("");
        }
        return new DocumentExcerptDto(excerpt);
    }

    private static List<String> parseAnchors(String anchorsParam) {
        if (anchorsParam == null || anchorsParam.isBlank()) {
            return List.of();
        }
        return Arrays.stream(anchorsParam.split("[,，]"))
                .map(String::trim)
                .filter(anchor -> !anchor.isBlank())
                .flatMap(anchor -> ChunkHeadingHeuristics.extractArticleAnchors(anchor).stream())
                .distinct()
                .toList();
    }
}
