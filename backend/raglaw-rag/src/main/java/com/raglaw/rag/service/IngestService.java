package com.raglaw.rag.service;

import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.exception.BusinessException;
import com.raglaw.common.util.Ids;
import com.raglaw.rag.domain.CategoryEntity;
import com.raglaw.rag.domain.DocStatus;
import com.raglaw.rag.domain.DocumentChunkEntity;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.ingest.MarkdownChunker;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.service.storage.DocumentStorageService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final CategoryService categoryService;
    private final DocumentStorageService documentStorageService;
    private final EmbeddingService embeddingService;
    private final ObjectProvider<VectorStoreService> vectorStoreService;

    public IngestService(
            DocumentRepository documentRepository,
            DocumentChunkRepository documentChunkRepository,
            CategoryService categoryService,
            DocumentStorageService documentStorageService,
            EmbeddingService embeddingService,
            ObjectProvider<VectorStoreService> vectorStoreService
    ) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.categoryService = categoryService;
        this.documentStorageService = documentStorageService;
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
    }

    @Transactional
    public void ingest(DocumentEntity document) {
        CategoryEntity category = categoryService.findEntity(document.getCategoryId());
        CategoryPaths paths = resolveCategoryPaths(category);

        String markdown = readContent(document.getMinioKey());
        List<String> chunks = MarkdownChunker.chunkByParagraph(markdown);

        documentChunkRepository.deleteByDocumentId(document.getId());
        VectorStoreService vectorStore = vectorStoreService.getIfAvailable();
        if (vectorStore != null && vectorStore.isEnabled()) {
            vectorStore.deleteByDocumentId(document.getId());
        }

        int index = 0;
        for (String chunkText : chunks) {
            String chunkId = Ids.newId();
            DocumentChunkEntity chunk = new DocumentChunkEntity(
                    chunkId,
                    document.getId(),
                    null,
                    index++,
                    chunkText,
                    paths.l1Path(),
                    paths.l2Path(),
                    paths.l3Path(),
                    null
            );
            documentChunkRepository.save(chunk);
            embedChunk(vectorStore, chunk, document.getDocType());
        }

        if (document.getStatus() == DocStatus.PENDING || document.getStatus() == DocStatus.REJECTED) {
            if ("CASE".equals(document.getDocType())) {
                document.setStatus(DocStatus.AWAITING_APPROVAL);
            } else {
                document.setStatus(DocStatus.INDEXED);
            }
            documentRepository.save(document);
        }
    }

    public InputStream download(String documentId) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "文档不存在"));
        return documentStorageService.load(document.getMinioKey());
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

    private String readContent(String storageKey) {
        try (InputStream inputStream = documentStorageService.load(storageKey)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
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
