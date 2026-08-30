package com.raglaw.rag.retrieval;

import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.retrieval.chunk.ChunkHeadingHeuristics;
import com.raglaw.rag.service.DocumentFullTextService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ReferenceExcerptEnhancer {

    private final DocumentRepository documentRepository;
    private final DocumentFullTextService documentFullTextService;
    private final QueryRewriteService queryRewriteService;
    private final RagProperties ragProperties;

    public ReferenceExcerptEnhancer(
            DocumentRepository documentRepository,
            DocumentFullTextService documentFullTextService,
            QueryRewriteService queryRewriteService,
            RagProperties ragProperties
    ) {
        this.documentRepository = documentRepository;
        this.documentFullTextService = documentFullTextService;
        this.queryRewriteService = queryRewriteService;
        this.ragProperties = ragProperties;
    }

    public String enhance(String excerpt, String documentId, String userQuery) {
        if (excerpt == null || excerpt.isBlank()) {
            return excerpt == null ? "" : excerpt;
        }
        if (!needsEnhancement(excerpt)) {
            return excerpt;
        }
        if (documentId == null || documentId.isBlank()) {
            return sanitizeExcerpt(excerpt);
        }
        String fullText = loadFullText(documentId);
        if (fullText.isBlank()) {
            return sanitizeExcerpt(excerpt);
        }
        int maxChars = ragProperties.getRetrieval().getDisplayExcerptMaxChars();
        String enhanced = tryEnhanceFromFullText(fullText, excerpt, userQuery, maxChars);
        if (isUsableEnhancedSnippet(enhanced)) {
            return enhanced;
        }
        return "";
    }

    private boolean needsEnhancement(String excerpt) {
        return ChunkHeadingHeuristics.looksLikeShortHeading(excerpt)
                || ChunkHeadingHeuristics.looksLikeTableOfContents(excerpt)
                || !ChunkHeadingHeuristics.containsSubstantiveArticleBody(excerpt);
    }

    private String sanitizeExcerpt(String excerpt) {
        if (ChunkHeadingHeuristics.looksLikeTableOfContents(excerpt)) {
            return "";
        }
        return excerpt;
    }

    private String loadFullText(String documentId) {
        DocumentEntity document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            return "";
        }
        if (document.getFullText() != null && !document.getFullText().isBlank()) {
            return document.getFullText();
        }
        return documentFullTextService.buildFullText(documentId);
    }

    private String tryEnhanceFromFullText(
            String fullText,
            String excerpt,
            String userQuery,
            int maxChars
    ) {
        List<String> excerptAnchors = ChunkHeadingHeuristics.extractArticleAnchors(excerpt);
        if (!excerptAnchors.isEmpty()) {
            String fromArticles = FullTextSnippetExtractor.extractAroundArticles(fullText, excerptAnchors, maxChars);
            if (isUsableEnhancedSnippet(fromArticles)) {
                return fromArticles;
            }
        }
        if (ChunkHeadingHeuristics.looksLikeTableOfContents(excerpt)
                && userQuery != null
                && !userQuery.isBlank()) {
            String fromQuery = FullTextSnippetExtractor.extractSkippingHeadings(
                    fullText,
                    queryRewriteService.rewriteForArticleRetrieval(userQuery),
                    maxChars
            );
            if (isUsableEnhancedSnippet(fromQuery)) {
                return fromQuery;
            }
            fromQuery = FullTextSnippetExtractor.extractSkippingHeadings(fullText, userQuery, maxChars);
            if (isUsableEnhancedSnippet(fromQuery)) {
                return fromQuery;
            }
        }
        String articleAnchor = ChunkHeadingHeuristics.extractArticleAnchor(excerpt);
        if (!articleAnchor.isBlank()) {
            String fromArticle = FullTextSnippetExtractor.extractAroundAnchor(fullText, articleAnchor, maxChars);
            if (isUsableEnhancedSnippet(fromArticle)) {
                return fromArticle;
            }
        }
        if (userQuery != null && !userQuery.isBlank()) {
            String fromQuery = FullTextSnippetExtractor.extractSkippingHeadings(
                    fullText,
                    queryRewriteService.rewrite(userQuery),
                    maxChars
            );
            if (isUsableEnhancedSnippet(fromQuery)) {
                return fromQuery;
            }
        }
        String chapterAnchor = ChunkHeadingHeuristics.extractChapterAnchor(excerpt);
        if (!chapterAnchor.isBlank()) {
            String fromChapter = FullTextSnippetExtractor.extractAroundAnchor(fullText, chapterAnchor, maxChars);
            if (isUsableEnhancedSnippet(fromChapter)) {
                return fromChapter;
            }
        }
        if (userQuery != null && !userQuery.isBlank()) {
            String fromQuery = FullTextSnippetExtractor.extractSkippingHeadings(fullText, userQuery, maxChars);
            if (isUsableEnhancedSnippet(fromQuery)) {
                return fromQuery;
            }
        }
        return "";
    }

    private static boolean isUsableEnhancedSnippet(String snippet) {
        return snippet != null
                && !snippet.isBlank()
                && !ChunkHeadingHeuristics.looksLikeShortHeading(snippet)
                && !ChunkHeadingHeuristics.looksLikeTableOfContents(snippet);
    }
}
