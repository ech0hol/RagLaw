package com.raglaw.rag.retrieval.chunk;

import com.raglaw.rag.domain.ChunkLevel;
import com.raglaw.rag.domain.DocumentChunkEntity;
import com.raglaw.rag.repository.DocumentChunkRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ChunkHierarchyResolver {

    private final DocumentChunkRepository documentChunkRepository;

    public ChunkHierarchyResolver(DocumentChunkRepository documentChunkRepository) {
        this.documentChunkRepository = documentChunkRepository;
    }

    public ResolvedChunk resolve(String matchedChunkId) {
        if (matchedChunkId == null || matchedChunkId.isBlank()) {
            return ResolvedChunk.empty();
        }
        DocumentChunkEntity matched = documentChunkRepository.findById(matchedChunkId).orElse(null);
        if (matched == null) {
            return ResolvedChunk.empty();
        }
        DocumentChunkEntity child = matched;
        DocumentChunkEntity micro = null;
        if (matched.getChunkLevel() == ChunkLevel.MICRO) {
            micro = matched;
            child = parentOf(matched).orElse(matched);
        }
        DocumentChunkEntity parent = parentOf(child).orElse(null);
        String referenceChunkId = child.getId();
        DocumentChunkEntity leaf = micro != null ? micro : child;
        String displayContent = buildDisplayContent(parent, child, leaf);
        String llmContent = buildLlmContent(parent, child, leaf);
        return new ResolvedChunk(matched.getId(), referenceChunkId, displayContent, llmContent);
    }

    private Optional<DocumentChunkEntity> parentOf(DocumentChunkEntity chunk) {
        if (chunk.getParentId() == null || chunk.getParentId().isBlank()) {
            return Optional.empty();
        }
        return documentChunkRepository.findById(chunk.getParentId());
    }

    private String buildDisplayContent(
            DocumentChunkEntity parent,
            DocumentChunkEntity child,
            DocumentChunkEntity leaf
    ) {
        String leafText = contentOf(leaf);
        if (isSubstantiveContent(leafText)) {
            return leafText;
        }
        String siblingContent = pickSubstantiveUnderNode(child, leaf);
        if (!siblingContent.isBlank()) {
            return siblingContent;
        }
        String parentScopeContent = pickSubstantiveParentScope(parent, child, leaf);
        if (!parentScopeContent.isBlank()) {
            return parentScopeContent;
        }
        String childText = contentOf(child);
        if (isSubstantiveContent(childText)) {
            return childText;
        }
        if (!leafText.isBlank() && isSubstantiveContent(childText) && !leafText.equals(childText)) {
            return childText + "\n" + leafText;
        }
        String parentText = contentOf(parent);
        if (isSubstantiveContent(parentText)) {
            return parentText;
        }
        return "";
    }

    private String pickSubstantiveUnderNode(DocumentChunkEntity node, DocumentChunkEntity leaf) {
        if (node == null || node.getId() == null || node.getId().isBlank()) {
            return "";
        }
        List<DocumentChunkEntity> descendants = documentChunkRepository.findByParentIdOrderByChunkIndexAsc(node.getId());
        return pickBestSubstantiveContent(descendants, node, leaf);
    }

    private String pickSubstantiveParentScope(
            DocumentChunkEntity parent,
            DocumentChunkEntity child,
            DocumentChunkEntity leaf
    ) {
        if (parent == null || parent.getId() == null || parent.getId().isBlank()) {
            return "";
        }
        List<DocumentChunkEntity> candidates = new ArrayList<>();
        List<DocumentChunkEntity> chapterChildren = documentChunkRepository.findByParentIdOrderByChunkIndexAsc(parent.getId());
        for (DocumentChunkEntity chapterChild : chapterChildren) {
            if (chapterChild.getChunkLevel() == ChunkLevel.CHILD) {
                String childContent = contentOf(chapterChild);
                if (isSubstantiveContent(childContent)) {
                    candidates.add(chapterChild);
                }
                candidates.addAll(documentChunkRepository.findByParentIdOrderByChunkIndexAsc(chapterChild.getId()));
            }
        }
        return pickBestSubstantiveContent(candidates, child, leaf);
    }

    private String pickBestSubstantiveContent(
            List<DocumentChunkEntity> chunks,
            DocumentChunkEntity child,
            DocumentChunkEntity leaf
    ) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        List<String> substantiveTexts = new ArrayList<>();
        for (DocumentChunkEntity chunk : chunks) {
            String text = contentOf(chunk);
            if (isSubstantiveContent(text)) {
                substantiveTexts.add(text);
            }
        }
        if (!substantiveTexts.isEmpty()) {
            return substantiveTexts.stream()
                    .max(Comparator.comparingInt(String::length))
                    .orElse(substantiveTexts.get(0));
        }
        DocumentChunkEntity longest = chunks.stream()
                .max(Comparator.comparingInt(chunk -> contentOf(chunk).length()))
                .orElse(null);
        if (longest == null) {
            return "";
        }
        String longestText = contentOf(longest);
        if (leaf != null && leaf.getId() != null && leaf.getId().equals(longest.getId())) {
            if (isSubstantiveContent(longestText)) {
                return longestText;
            }
            return "";
        }
        String childText = contentOf(child);
        if (!childText.isBlank()
                && ChunkHeadingHeuristics.looksLikeShortHeading(childText)
                && isSubstantiveContent(longestText)) {
            return longestText;
        }
        if (isSubstantiveContent(longestText)) {
            return longestText;
        }
        return "";
    }

    private static boolean isSubstantiveContent(String text) {
        return !text.isBlank() && !ChunkHeadingHeuristics.looksLikeShortHeading(text);
    }

    private static String contentOf(DocumentChunkEntity chunk) {
        if (chunk == null || chunk.getContent() == null) {
            return "";
        }
        return chunk.getContent().trim();
    }

    private static String buildLlmContent(DocumentChunkEntity parent, DocumentChunkEntity child, DocumentChunkEntity leaf) {
        StringBuilder builder = new StringBuilder();
        if (parent != null && parent.getContent() != null && !parent.getContent().isBlank()) {
            builder.append(parent.getContent().trim());
        }
        if (child != null && child.getContent() != null && !child.getContent().isBlank()) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(child.getContent().trim());
        }
        if (leaf != null && leaf.getId() != null && child != null && !leaf.getId().equals(child.getId())
                && leaf.getContent() != null && !leaf.getContent().isBlank()) {
            builder.append('\n').append(leaf.getContent().trim());
        }
        return builder.toString();
    }

    public record ResolvedChunk(
            String matchedChunkId,
            String referenceChunkId,
            String displayContent,
            String llmContent
    ) {
        public static ResolvedChunk empty() {
            return new ResolvedChunk(null, null, "", "");
        }
    }
}
