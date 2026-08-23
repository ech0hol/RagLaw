package com.raglaw.rag.service;

import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.exception.BusinessException;
import com.raglaw.common.util.Ids;
import com.raglaw.rag.contract.ContractClassifier;
import com.raglaw.rag.contract.ContractRiskAnalyzer;
import com.raglaw.rag.domain.CategoryEntity;
import com.raglaw.rag.domain.DocStatus;
import com.raglaw.rag.domain.DocumentChunkEntity;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.domain.IngestStage;
import com.raglaw.rag.ingest.DocumentTextExtractor;
import com.raglaw.rag.ingest.MarkdownChunker;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.service.storage.DocumentStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestPipeline {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final CategoryService categoryService;
    private final DocumentStorageService documentStorageService;
    private final DocumentTextExtractor documentTextExtractor;
    private final ContractClassifier contractClassifier;
    private final ContractRiskAnalyzer contractRiskAnalyzer;
    private final EmbeddingService embeddingService;
    private final ObjectProvider<VectorStoreService> vectorStoreService;
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
            ObjectProvider<VectorStoreService> vectorStoreService,
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
        this.vectorStoreService = vectorStoreService;
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
        }

        List<MarkdownChunker.ChunkDraft> drafts = MarkdownChunker.chunkHierarchy(extraction.text());

        documentChunkRepository.deleteByDocumentId(document.getId());
        VectorStoreService vectorStore = vectorStoreService.getIfAvailable();
        if (vectorStore != null && vectorStore.isEnabled()) {
            vectorStore.deleteByDocumentId(document.getId());
        }

        persistChunks(document, paths, drafts);

        if ("CONTRACT".equals(document.getDocType())) {
            contractRiskAnalyzer.analyze(document.getId());
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

        List<DocumentChunkEntity> chunks = documentChunkRepository.findByDocumentIdOrderByChunkIndexAsc(document.getId());
        VectorStoreService vectorStore = vectorStoreService.getIfAvailable();
        if (vectorStore != null && vectorStore.isEnabled()) {
            vectorStore.deleteByDocumentId(document.getId());
        }

        boolean hasChildren = chunks.stream().anyMatch(chunk -> chunk.getParentId() != null);
        for (DocumentChunkEntity chunk : chunks) {
            if (hasChildren && chunk.getParentId() == null) {
                continue;
            }
            embedChunk(vectorStore, chunk, document.getDocType());
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
        Map<String, String> parentIds = new HashMap<>();
        int chunkIndex = 0;
        for (MarkdownChunker.ChunkDraft draft : drafts) {
            if (draft.isParent()) {
                String chunkId = Ids.newId();
                parentIds.put(draft.localId(), chunkId);
                documentChunkRepository.save(new DocumentChunkEntity(
                        chunkId,
                        document.getId(),
                        null,
                        chunkIndex++,
                        draft.content(),
                        paths.l1Path(),
                        paths.l2Path(),
                        paths.l3Path(),
                        null
                ));
            }
        }
        for (MarkdownChunker.ChunkDraft draft : drafts) {
            if (draft.isChild()) {
                String parentId = parentIds.get(draft.parentLocalId());
                documentChunkRepository.save(new DocumentChunkEntity(
                        Ids.newId(),
                        document.getId(),
                        parentId,
                        chunkIndex++,
                        draft.content(),
                        paths.l1Path(),
                        paths.l2Path(),
                        paths.l3Path(),
                        null
                ));
            } else if (!draft.isParent()) {
                documentChunkRepository.save(new DocumentChunkEntity(
                        Ids.newId(),
                        document.getId(),
                        null,
                        chunkIndex++,
                        draft.content(),
                        paths.l1Path(),
                        paths.l2Path(),
                        paths.l3Path(),
                        null
                ));
            }
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

    private void embedChunk(VectorStoreService vectorStore, DocumentChunkEntity chunk, String docType) {
        if (vectorStore == null || !vectorStore.isEnabled() || !embeddingService.isEnabled()) {
            return;
        }
        Optional<float[]> embedding = embeddingService.embed(chunk.getContent());
        embedding.ifPresent(vector -> vectorStore.upsert(
                chunk.getId(),
                chunk.getDocumentId(),
                vector,
                chunk.getL1Path(),
                chunk.getL2Path(),
                chunk.getL3Path(),
                docType
        ));
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
