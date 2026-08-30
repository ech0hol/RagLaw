package com.raglaw.rag.service;

import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.exception.BusinessException;
import com.raglaw.common.util.Ids;
import com.raglaw.rag.contract.ContractClassifier;
import com.raglaw.rag.contract.ContractRiskAnalyzer;
import com.raglaw.rag.domain.CategoryEntity;
import com.raglaw.rag.domain.ChunkLevel;
import com.raglaw.rag.domain.DocStatus;
import com.raglaw.rag.domain.DocumentChunkEntity;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.domain.IngestStage;
import com.raglaw.rag.ingest.DisplayChunkFilter;
import com.raglaw.rag.ingest.DocumentTextExtractor;
import com.raglaw.rag.ingest.MarkdownChunker;
import com.raglaw.rag.ingest.StatuteMetadataExtractor;
import com.raglaw.rag.retrieval.chunk.ChunkHeadingHeuristics;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.search.ElasticsearchIndexService;
import com.raglaw.rag.search.ElasticsearchRetriever;
import com.raglaw.rag.service.storage.DocumentStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestPipeline {

    private static final Logger log = LoggerFactory.getLogger(IngestPipeline.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final CategoryService categoryService;
    private final DocumentStorageService documentStorageService;
    private final DocumentTextExtractor documentTextExtractor;
    private final ContractClassifier contractClassifier;
    private final ContractRiskAnalyzer contractRiskAnalyzer;
    private final EmbeddingService embeddingService;
    private final ObjectProvider<ElasticsearchRetriever> elasticsearchRetriever;
    private final IndexOutboxService indexOutboxService;
    private final CaseStatuteLinker caseStatuteLinker;
    private final ObjectMapper objectMapper;

    public IngestPipeline(
            DocumentRepository documentRepository,
            DocumentChunkRepository documentChunkRepository,
            CategoryService categoryService,
            DocumentStorageService documentStorageService,
            DocumentTextExtractor documentTextExtractor,
            ContractClassifier contractClassifier,
            ContractRiskAnalyzer contractRiskAnalyzer,
            EmbeddingService embeddingService,
            ObjectProvider<ElasticsearchRetriever> elasticsearchRetriever,
            IndexOutboxService indexOutboxService,
            CaseStatuteLinker caseStatuteLinker,
            ObjectMapper objectMapper
    ) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.categoryService = categoryService;
        this.documentStorageService = documentStorageService;
        this.documentTextExtractor = documentTextExtractor;
        this.contractClassifier = contractClassifier;
        this.contractRiskAnalyzer = contractRiskAnalyzer;
        this.embeddingService = embeddingService;
        this.elasticsearchRetriever = elasticsearchRetriever;
        this.indexOutboxService = indexOutboxService;
        this.caseStatuteLinker = caseStatuteLinker;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void sync(DocumentEntity document) {
        parse(document);
        index(document);
    }

    @Transactional
    public void parse(DocumentEntity document) {
        document.setIngestStage(IngestStage.PARSING);
        document.setIngestError(null);
        documentRepository.save(document);

        CategoryEntity category = categoryService.findEntity(document.getCategoryId());
        CategoryPaths paths = resolveCategoryPaths(category);

        DocumentTextExtractor.ExtractionResult extraction = readContent(document.getMinioKey());
        if ("CONTRACT".equals(document.getDocType())) {
            applyContractMetadata(document, extraction);
        } else {
            applyExtractMetadata(document, extraction);
        }

        document.setFullText(extraction.text());

        List<MarkdownChunker.ChunkDraft> drafts = MarkdownChunker.chunkHierarchy(extraction.text());

        documentChunkRepository.deleteByDocumentId(document.getId());
        if (isElasticsearchIndexingEnabled()) {
            indexOutboxService.enqueueDeleteDocument(document.getId(), document.getIndexVersion());
        }

        persistChunks(document, paths, drafts);

        if (document.getFullText() == null || document.getFullText().isBlank()) {
            log.warn("full_text empty after extraction for {}, falling back to chunk text", document.getId());
            document.setFullText(buildFullTextFromChunks(document.getId()));
        }

        if ("CASE".equals(document.getDocType())) {
            caseStatuteLinker.linkCaseToStatutes(document.getId());
        }

        document.setIngestStage(IngestStage.PARSED);
        documentRepository.save(document);
    }

    @Transactional
    public void index(DocumentEntity document) {
        document.setIngestStage(IngestStage.INDEXING);
        documentRepository.save(document);

        if ("CASE".equals(document.getDocType()) && document.getStatus() != DocStatus.INDEXED) {
            document.setStatus(DocStatus.AWAITING_APPROVAL);
            document.setIngestStage(IngestStage.PARSED);
            documentRepository.save(document);
            return;
        }

        if ("CONTRACT".equals(document.getDocType())) {
            if (document.getStatus() == DocStatus.PENDING || document.getStatus() == DocStatus.REJECTED) {
                document.setStatus(DocStatus.INDEXED);
            }
            document.setIngestStage(IngestStage.INDEXED);
            document.setIngestError(null);
            documentRepository.save(document);
            contractRiskAnalyzer.analyze(document.getId());
            return;
        }

        List<DocumentChunkEntity> chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(document.getId());
        try {
            indexEmbeddableChunks(document, chunks);
        } catch (java.io.IOException ex) {
            markFailed(document, ex);
            return;
        }

        if (document.getStatus() == DocStatus.PENDING || document.getStatus() == DocStatus.REJECTED) {
            if (!"CASE".equals(document.getDocType())) {
                document.setStatus(DocStatus.INDEXED);
            }
        }
        document.setIngestStage(IngestStage.INDEXED);
        document.setIngestError(null);
        documentRepository.save(document);
    }

    public void markFailed(DocumentEntity document, Exception ex) {
        document.setIngestStage(IngestStage.FAILED);
        document.setIngestError(ex.getMessage());
        documentRepository.save(document);
    }

    private void persistChunks(
            DocumentEntity document,
            CategoryPaths paths,
            List<MarkdownChunker.ChunkDraft> drafts
    ) {
        if (drafts.isEmpty()) {
            return;
        }
        Map<String, String> localToId = new HashMap<>();
        int chunkIndex = 0;

        for (MarkdownChunker.ChunkDraft draft : drafts) {
            if (!draft.isParent()) {
                continue;
            }
            String chunkId = Ids.newId();
            localToId.put(draft.localId(), chunkId);
            documentChunkRepository.save(new DocumentChunkEntity(
                    chunkId,
                    document.getId(),
                    null,
                    ChunkLevel.PARENT,
                    chunkIndex++,
                    draft.content(),
                    paths.l1Path(),
                    paths.l2Path(),
                    paths.l3Path(),
                    null
            ));
        }

        for (MarkdownChunker.ChunkDraft draft : drafts) {
            if (!draft.isChild()) {
                continue;
            }
            String parentId = draft.parentLocalId() != null ? localToId.get(draft.parentLocalId()) : null;
            String chunkId = Ids.newId();
            if (draft.localId() != null) {
                localToId.put(draft.localId(), chunkId);
            }
            documentChunkRepository.save(new DocumentChunkEntity(
                    chunkId,
                    document.getId(),
                    parentId,
                    ChunkLevel.CHILD,
                    chunkIndex++,
                    draft.content(),
                    paths.l1Path(),
                    paths.l2Path(),
                    paths.l3Path(),
                    null
            ));
        }

        for (MarkdownChunker.ChunkDraft draft : drafts) {
            if (!draft.isMicro()) {
                continue;
            }
            String parentId = draft.parentLocalId() != null ? localToId.get(draft.parentLocalId()) : null;
            documentChunkRepository.save(new DocumentChunkEntity(
                    Ids.newId(),
                    document.getId(),
                    parentId,
                    ChunkLevel.MICRO,
                    chunkIndex++,
                    draft.content(),
                    paths.l1Path(),
                    paths.l2Path(),
                    paths.l3Path(),
                    null
            ));
        }

        for (MarkdownChunker.ChunkDraft draft : drafts) {
            if (draft.isParent() || draft.isChild() || draft.isMicro()) {
                continue;
            }
            documentChunkRepository.save(new DocumentChunkEntity(
                    Ids.newId(),
                    document.getId(),
                    null,
                    ChunkLevel.CHILD,
                    chunkIndex++,
                    draft.content(),
                    paths.l1Path(),
                    paths.l2Path(),
                    paths.l3Path(),
                    null
            ));
        }
    }

    private void applyContractMetadata(DocumentEntity document, DocumentTextExtractor.ExtractionResult extraction) {
        ContractClassifier.ClassificationResult classification = contractClassifier.classify(extraction.text());
        try {
            document.setMetadataJson(objectMapper.writeValueAsString(Map.of(
                    "contractDomain", classification.domain(),
                    "suggestedAgentCode", classification.suggestedAgentCode(),
                    "keywordHits", classification.keywordHits(),
                    "extractMethod", extraction.method(),
                    "ocrUsed", extraction.ocrUsed()
            )));
        } catch (Exception ex) {
            document.setMetadataJson(classification.toMetadataJson());
        }
        documentRepository.save(document);
    }

    private void applyExtractMetadata(DocumentEntity document, DocumentTextExtractor.ExtractionResult extraction) {
        try {
            Map<String, Object> metadata = readMetadataMap(document.getMetadataJson());
            metadata.put("extractMethod", extraction.method());
            metadata.put("ocrUsed", extraction.ocrUsed());
            if ("STATUTE".equals(document.getDocType()) || "CASE".equals(document.getDocType())) {
                StatuteMetadataExtractor.EffectiveDateResult effectiveDate = StatuteMetadataExtractor.extract(
                        document.getTitle(),
                        extraction.text()
                );
                if (effectiveDate != null && effectiveDate.effectiveDate() != null) {
                    metadata.put("effectiveDate", effectiveDate.effectiveDate());
                    metadata.put("effectiveDateSource", effectiveDate.effectiveDateSource());
                }
            }
            document.setMetadataJson(objectMapper.writeValueAsString(metadata));
        } catch (Exception ex) {
            // keep existing metadata
        }
        documentRepository.save(document);
    }

    private Map<String, Object> readMetadataMap(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(
                    metadataJson,
                    objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class)
            );
        } catch (Exception ex) {
            return new HashMap<>();
        }
    }

    private String buildFullTextFromChunks(String documentId) {
        List<DocumentChunkEntity> chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        return chunks.stream()
                .filter(DisplayChunkFilter::includeInFullText)
                .map(DocumentChunkEntity::getContent)
                .filter(content -> content != null && !content.isBlank())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
    }

    private boolean isElasticsearchIndexingEnabled() {
        ElasticsearchRetriever retriever = elasticsearchRetriever.getIfAvailable();
        return retriever != null && retriever.isEnabled() && embeddingService.isEnabled();
    }

    private void indexEmbeddableChunks(DocumentEntity document, List<DocumentChunkEntity> chunks)
            throws java.io.IOException {
        if (!isElasticsearchIndexingEnabled()) {
            return;
        }
        List<DocumentChunkEntity> embeddable = selectEmbeddableChunks(chunks);
        List<ElasticsearchIndexService.ChunkIndexEntry> entries = new ArrayList<>();
        for (DocumentChunkEntity chunk : embeddable) {
            Optional<float[]> embedding = embeddingService.embedDocument(chunk.getContent());
            if (embedding.isEmpty()) {
                continue;
            }
            entries.add(new ElasticsearchIndexService.ChunkIndexEntry(
                    chunk.getId(),
                    chunk.getContent(),
                    embedding.get(),
                    chunk.getL1Path(),
                    chunk.getL2Path(),
                    chunk.getL3Path()
            ));
        }
        document.bumpIndexVersion();
        ElasticsearchRetriever retriever = elasticsearchRetriever.getIfAvailable();
        if (retriever != null) {
            retriever.deleteByDocumentIdOrThrow(document.getId());
            retriever.bulkIndexDocumentOrThrow(
                    document.getId(),
                    document.getDocType(),
                    entries,
                    document.getIndexVersion()
            );
        }
        documentRepository.save(document);
    }

    public int countEmbeddableChunks(String documentId) {
        List<DocumentChunkEntity> chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        return selectEmbeddableChunks(chunks).size();
    }

    private List<DocumentChunkEntity> selectEmbeddableChunks(List<DocumentChunkEntity> chunks) {
        boolean hasMicro = chunks.stream().anyMatch(chunk -> chunk.getChunkLevel() == ChunkLevel.MICRO);
        boolean hasChildren = chunks.stream().anyMatch(chunk -> chunk.getChunkLevel() == ChunkLevel.CHILD
                || (chunk.getChunkLevel() == null && chunk.getParentId() != null));
        List<DocumentChunkEntity> selected = new ArrayList<>();
        for (DocumentChunkEntity chunk : chunks) {
            if (chunk.getChunkLevel() == ChunkLevel.PARENT
                    || (chunk.getChunkLevel() == null && chunk.getParentId() == null && hasChildren)) {
                continue;
            }
            if (hasMicro && chunk.getChunkLevel() != ChunkLevel.MICRO) {
                continue;
            }
            if (chunk.getChunkLevel() == ChunkLevel.MICRO
                    && ChunkHeadingHeuristics.looksLikeShortHeading(chunk.getContent())) {
                continue;
            }
            if (ChunkHeadingHeuristics.looksLikeTableOfContents(chunk.getContent())) {
                continue;
            }
            selected.add(chunk);
        }
        return selected;
    }

    private DocumentTextExtractor.ExtractionResult readContent(String storageKey) {
        try (InputStream inputStream = documentStorageService.load(storageKey)) {
            return documentTextExtractor.extractFromStorageKey(inputStream, storageKey);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCodes.INTERNAL, "读取文档内容失败");
        }
    }

    private CategoryPaths resolveCategoryPaths(CategoryEntity l3Category) {
        if (l3Category.getLevel() != 3) {
            throw new BusinessException(ErrorCodes.VALIDATION, "入库需要 L3 类目");
        }
        CategoryEntity l2 = categoryService.findEntity(l3Category.getParentId());
        CategoryEntity l1 = categoryService.findEntity(l2.getParentId());
        return new CategoryPaths(l1.getPath(), l2.getPath(), l3Category.getPath());
    }

    private record CategoryPaths(String l1Path, String l2Path, String l3Path) {
    }
}
