package com.raglaw.rag.contract;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raglaw.common.api.ErrorCodes;
import com.raglaw.common.auth.CurrentUserHolder;
import com.raglaw.common.exception.BusinessException;
import com.raglaw.common.util.Ids;
import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.domain.ChunkLevel;
import com.raglaw.rag.domain.ContractRiskEntity;
import com.raglaw.rag.domain.DocumentChunkEntity;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.dto.HighlightRect;
import com.raglaw.rag.dto.LegalReferenceDto;
import com.raglaw.rag.ingest.PdfPageLocator;
import com.raglaw.rag.ingest.PdfTextLocator;
import com.raglaw.rag.llm.LlmChatClient;
import com.raglaw.rag.contract.trace.ContractReviewTracePort;
import com.raglaw.rag.repository.ContractRiskRepository;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.service.storage.DocumentStorageService;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractRiskAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(ContractRiskAnalyzer.class);
    private static final int LLM_BATCH_MAX_ATTEMPTS = 3;
    private static final long LLM_BATCH_RETRY_DELAY_MS = 1500L;

    private static final String SYSTEM_PROMPT = """
            你是资深合同审查律师助手。根据提供的法规/案例摘录与合同段落，识别风险并给出修订建议。
            必须仅输出合法 JSON，格式为 {"risks":[...]}。
            每条风险字段：
            - chunkIndex: 整数，对应输入段落的全局索引（从 0 开始）
            - severity: HIGH / MEDIUM / LOW
            - dimension: 风险维度（含条款风险或文法/表述类）
            - summary: 简短风险说明
            - excerpt: 必须从对应段落原文中连续复制，不得编造
            - suggestion: 具体修订建议或可替换条款文本
            - legalBasis: 字符串数组，优先引用提供的法规/案例摘录
            若无风险则 risks 为空数组。不要输出 markdown 或额外说明。
            """;

    private final DocumentChunkRepository chunkRepository;
    private final ContractRiskRepository riskRepository;
    private final DocumentRepository documentRepository;
    private final DocumentStorageService documentStorageService;
    private final PdfPageLocator pdfPageLocator;
    private final PdfTextLocator pdfTextLocator;
    private final ObjectMapper objectMapper;
    private final LlmChatClient llmChatClient;
    private final RagProperties ragProperties;
    private final ContractRagContextBuilder ragContextBuilder;
    private final ContractReviewTracePort contractReviewTracePort;

    public ContractRiskAnalyzer(
            DocumentChunkRepository chunkRepository,
            ContractRiskRepository riskRepository,
            DocumentRepository documentRepository,
            DocumentStorageService documentStorageService,
            PdfPageLocator pdfPageLocator,
            PdfTextLocator pdfTextLocator,
            ObjectMapper objectMapper,
            LlmChatClient llmChatClient,
            RagProperties ragProperties,
            ContractRagContextBuilder ragContextBuilder,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            ContractReviewTracePort contractReviewTracePort
    ) {
        this.chunkRepository = chunkRepository;
        this.riskRepository = riskRepository;
        this.documentRepository = documentRepository;
        this.documentStorageService = documentStorageService;
        this.pdfPageLocator = pdfPageLocator;
        this.pdfTextLocator = pdfTextLocator;
        this.objectMapper = objectMapper;
        this.llmChatClient = llmChatClient;
        this.ragProperties = ragProperties;
        this.ragContextBuilder = ragContextBuilder;
        this.contractReviewTracePort = contractReviewTracePort;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ContractRiskEntity> analyze(String documentId) {
        if (!ragProperties.getContract().isEnabled()) {
            throw new BusinessException(ErrorCodes.VALIDATION, "合同 LLM 审查未启用");
        }

        long analyzeStartMs = System.currentTimeMillis();
        riskRepository.deleteByDocumentId(documentId);
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.NOT_FOUND, "文档不存在"));

        String traceId = startContractTrace(document);

        updateReviewStatus(document, ContractReviewStatus.RUNNING, null, null, 0, 0, 0);

        List<DocumentChunkEntity> allChunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        List<DocumentChunkEntity> reviewChunks = selectReviewChunks(allChunks);

        if (reviewChunks.isEmpty()) {
            updateReviewStatus(
                    document,
                    ContractReviewStatus.SKIPPED_NO_CHUNKS,
                    "文档尚未完成解析或无可用段落，请重新上传或等待入库完成",
                    null,
                    0,
                    0,
                    0
            );
            completeContractTrace(traceId, analyzeStartMs, 0);
            return List.of();
        }

        String suggestedAgentCode = readSuggestedAgentCode(document);
        long ragStartMs = System.currentTimeMillis();
        ContractRagContextBuilder.ContractRagContext initialRagContext = ragContextBuilder.build(
                document,
                suggestedAgentCode,
                reviewChunks.stream().map(DocumentChunkEntity::getContent).reduce("", String::concat)
        );
        recordContractRagTrace(traceId, initialRagContext, System.currentTimeMillis() - ragStartMs);
        int totalRagHits = initialRagContext.hitCount();

        byte[] pdfBytes = loadPdfBytes(document);
        boolean isPdf = pdfBytes != null;

        List<ContractRiskEntity> risks = new ArrayList<>();
        int batchSize = Math.max(1, ragProperties.getContract().getChunksPerBatch());
        int maxRisks = ragProperties.getContract().getMaxRisksPerDocument();
        String model = ragProperties.getContract().getModel();
        int totalBatches = (reviewChunks.size() + batchSize - 1) / batchSize;
        int successfulBatches = 0;
        int failedBatches = 0;
        String lastBatchError = null;

        for (int batchStart = 0; batchStart < reviewChunks.size() && risks.size() < maxRisks; batchStart += batchSize) {
            int batchEnd = Math.min(batchStart + batchSize, reviewChunks.size());
            List<DocumentChunkEntity> batch = reviewChunks.subList(batchStart, batchEnd);
            ContractRagContextBuilder.ContractRagContext ragContext = ragContextBuilder.buildForBatch(
                    document,
                    suggestedAgentCode,
                    batch,
                    null
            );
            String userMessage = buildUserMessage(ragContext.promptBlock(), batch, batchStart);
            long batchStartMs = System.currentTimeMillis();
            try {
                String json = completeJsonWithRetry(userMessage, model);
                List<ContractRiskEntity> batchRisks = parseRisks(
                        json,
                        documentId,
                        reviewChunks,
                        batchStart,
                        ragContext.references(),
                        isPdf,
                        pdfBytes
                );
                successfulBatches++;
                recordContractLlmBatch(traceId, batchStart, batchEnd, batchRisks.size(), userMessage, json, model, batchStartMs);
                for (ContractRiskEntity risk : batchRisks) {
                    if (risks.size() < maxRisks) {
                        risks.add(risk);
                    }
                }
            } catch (IllegalStateException ex) {
                failedBatches++;
                lastBatchError = ex.getMessage();
                log.warn("Contract LLM batch failed documentId={} batchStart={}: {}", documentId, batchStart, ex.getMessage());
            } catch (Exception ex) {
                failedBatches++;
                lastBatchError = ex.getMessage();
                log.warn("Contract LLM batch failed documentId={} batchStart={}: {}", documentId, batchStart, ex.getMessage());
            }
        }

        if (successfulBatches == 0) {
            String error = lastBatchError != null && !lastBatchError.isBlank()
                    ? lastBatchError
                    : "合同 LLM 审查失败，请检查 API 配置后重试";
            updateReviewStatus(
                    document,
                    ContractReviewStatus.FAILED,
                    error,
                    null,
                    totalRagHits,
                    totalBatches,
                    failedBatches
            );
            completeContractTrace(traceId, analyzeStartMs, 0);
            return List.of();
        }

        String finalStatus = failedBatches > 0 ? ContractReviewStatus.PARTIAL : ContractReviewStatus.COMPLETED;
        updateReviewStatus(
                document,
                finalStatus,
                failedBatches > 0 ? "部分条款批次审查失败，结果可能不完整" : null,
                model,
                totalRagHits,
                successfulBatches,
                failedBatches
        );
        List<ContractRiskEntity> saved = riskRepository.saveAll(risks);
        completeContractTrace(traceId, analyzeStartMs, saved.size());
        return saved;
    }

    private String startContractTrace(DocumentEntity document) {
        if (contractReviewTracePort == null) {
            return null;
        }
        String userId = CurrentUserHolder.get();
        return contractReviewTracePort.start(
                userId == null ? "" : userId,
                document.getId(),
                document.getTitle()
        );
    }

    private void recordContractRagTrace(
            String traceId,
            ContractRagContextBuilder.ContractRagContext ragContext,
            long durationMs
    ) {
        if (contractReviewTracePort == null || traceId == null) {
            return;
        }
        contractReviewTracePort.stage(
                traceId,
                "contract_rag_context",
                Map.of("hitCount", ragContext.hitCount()),
                durationMs
        );
        contractReviewTracePort.recordRagHits(traceId, ragContext.hits());
    }

    private void recordContractLlmBatch(
            String traceId,
            int batchStart,
            int batchEnd,
            int riskCount,
            String userMessage,
            String json,
            String model,
            long batchStartMs
    ) {
        if (contractReviewTracePort == null || traceId == null) {
            return;
        }
        long durationMs = System.currentTimeMillis() - batchStartMs;
        contractReviewTracePort.stage(
                traceId,
                "contract_llm_batch",
                Map.of(
                        "batchStart", batchStart,
                        "batchEnd", batchEnd,
                        "riskCount", riskCount
                ),
                durationMs
        );
        contractReviewTracePort.recordLlmUsage(
                traceId,
                model,
                estimateTokens(userMessage),
                estimateTokens(json),
                durationMs
        );
    }

    private void completeContractTrace(String traceId, long analyzeStartMs, int riskCount) {
        if (contractReviewTracePort == null || traceId == null) {
            return;
        }
        contractReviewTracePort.complete(traceId, System.currentTimeMillis() - analyzeStartMs, riskCount);
    }

    private static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / 2);
    }

    private String completeJsonWithRetry(String userMessage, String model) {
        IllegalStateException lastError = null;
        for (int attempt = 1; attempt <= LLM_BATCH_MAX_ATTEMPTS; attempt++) {
            try {
                return llmChatClient.completeJson(SYSTEM_PROMPT, userMessage, model);
            } catch (IllegalStateException ex) {
                lastError = ex;
                if (attempt < LLM_BATCH_MAX_ATTEMPTS && isTransientLlmError(ex)) {
                    log.warn("Contract LLM batch transient error, retrying attempt={}: {}", attempt, ex.getMessage());
                    try {
                        Thread.sleep(LLM_BATCH_RETRY_DELAY_MS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw ex;
                    }
                    continue;
                }
                throw ex;
            }
        }
        throw lastError != null ? lastError : new IllegalStateException("合同 LLM 审查失败");
    }

    private static boolean isTransientLlmError(IllegalStateException ex) {
        String message = ex.getMessage() != null ? ex.getMessage().toLowerCase(Locale.ROOT) : "";
        return message.contains("connection reset")
                || message.contains("connection refused")
                || message.contains("timeout")
                || message.contains("timed out")
                || message.contains("503")
                || message.contains("429")
                || message.contains("broken pipe");
    }

    private static List<DocumentChunkEntity> selectReviewChunks(List<DocumentChunkEntity> allChunks) {
        if (allChunks == null || allChunks.isEmpty()) {
            return List.of();
        }
        List<DocumentChunkEntity> childChunks = allChunks.stream()
                .filter(chunk -> chunk.getChunkLevel() == ChunkLevel.CHILD)
                .toList();
        if (!childChunks.isEmpty()) {
            return childChunks;
        }
        List<DocumentChunkEntity> microChunks = allChunks.stream()
                .filter(chunk -> chunk.getChunkLevel() == ChunkLevel.MICRO)
                .toList();
        if (!microChunks.isEmpty()) {
            return microChunks;
        }
        return allChunks.stream()
                .filter(chunk -> chunk.getChunkLevel() != ChunkLevel.PARENT)
                .toList();
    }

    private void updateReviewStatus(
            DocumentEntity document,
            String status,
            String reviewError,
            String model,
            int ragHitCount,
            int successfulBatches,
            int failedBatches
    ) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            if (document.getMetadataJson() != null && !document.getMetadataJson().isBlank()) {
                metadata.putAll(objectMapper.readValue(document.getMetadataJson(), new TypeReference<Map<String, Object>>() {
                }));
            }
            metadata.put("contractReviewStatus", status);
            if (reviewError != null) {
                metadata.put("contractReviewError", reviewError);
            } else {
                metadata.remove("contractReviewError");
            }
            metadata.put("contractRagHitCount", ragHitCount);
            metadata.put("contractLlmBatchSucceeded", successfulBatches);
            metadata.put("contractLlmBatchFailed", failedBatches);
            if (ContractReviewStatus.COMPLETED.equals(status) && model != null && !model.isBlank()) {
                metadata.put("contractAnalysisModel", model);
                metadata.put("contractAnalysisSource", "LLM");
            } else if (!ContractReviewStatus.COMPLETED.equals(status)) {
                metadata.remove("contractAnalysisModel");
                metadata.remove("contractAnalysisSource");
            }
            document.setMetadataJson(objectMapper.writeValueAsString(metadata));
            documentRepository.save(document);
        } catch (Exception ex) {
            log.warn("Failed to update contract review metadata: {}", ex.getMessage());
        }
    }

    private String readSuggestedAgentCode(DocumentEntity document) {
        if (document.getMetadataJson() == null || document.getMetadataJson().isBlank()) {
            return "CONTRACT";
        }
        try {
            JsonNode node = objectMapper.readTree(document.getMetadataJson());
            String code = node.path("suggestedAgentCode").asText("");
            return code.isBlank() ? "CONTRACT" : code;
        } catch (Exception ex) {
            return "CONTRACT";
        }
    }

    private byte[] loadPdfBytes(DocumentEntity document) {
        String filename = resolveOriginalFilename(document);
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".pdf") || document.getMinioKey() == null) {
            return null;
        }
        try (InputStream inputStream = documentStorageService.load(document.getMinioKey())) {
            return inputStream.readAllBytes();
        } catch (Exception ex) {
            return null;
        }
    }

    private String buildUserMessage(
            String ragBlock,
            List<DocumentChunkEntity> batch,
            int globalStartIndex
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("【法律依据摘录】\n").append(ragBlock).append("\n\n【合同段落】\n");
        for (int i = 0; i < batch.size(); i++) {
            int globalIndex = globalStartIndex + i;
            sb.append("chunkIndex=").append(globalIndex)
                    .append(" content=").append(batch.get(i).getContent())
                    .append("\n");
        }
        return sb.toString();
    }

    private List<ContractRiskEntity> parseRisks(
            String json,
            String documentId,
            List<DocumentChunkEntity> allChildChunks,
            int batchStartIndex,
            List<ContractRagContextBuilder.LegalReference> ragReferences,
            boolean isPdf,
            byte[] pdfBytes
    ) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode risksNode = root.path("risks");
        if (!risksNode.isArray()) {
            return List.of();
        }

        List<ContractRiskEntity> results = new ArrayList<>();
        for (JsonNode node : risksNode) {
            int chunkIndex = node.path("chunkIndex").asInt(-1);
            if (chunkIndex < 0 || chunkIndex >= allChildChunks.size()) {
                continue;
            }
            DocumentChunkEntity chunk = allChildChunks.get(chunkIndex);
            String severity = normalizeSeverity(node.path("severity").asText("MEDIUM"));
            String dimension = trimTo(node.path("dimension").asText("条款风险"), 64);
            String summary = trimTo(node.path("summary").asText(""), 512);
            String rawExcerpt = node.path("excerpt").asText("");
            String suggestion = node.path("suggestion").asText("");
            if (summary.isBlank() || suggestion.isBlank()) {
                continue;
            }

            String excerpt = resolveExcerpt(chunk.getContent(), rawExcerpt);
            if (excerpt.isBlank()) {
                log.warn("Skipping risk with invalid excerpt documentId={} chunkIndex={}", documentId, chunkIndex);
                continue;
            }
            if (excerpt.length() > 200) {
                excerpt = excerpt.substring(0, 200) + "…";
            }

            Integer pageNumber = null;
            String highlightRectsJson = null;
            if (isPdf && pdfBytes != null) {
                List<HighlightRect> rects = pdfTextLocator.findRects(pdfBytes, excerpt);
                if (!rects.isEmpty()) {
                    pageNumber = rects.get(0).page();
                    highlightRectsJson = writeRects(rects);
                } else {
                    pageNumber = pdfPageLocator.findPage(pdfBytes, excerpt);
                }
            }

            ContractRiskEntity risk = new ContractRiskEntity(
                    Ids.newId(),
                    documentId,
                    chunk.getId(),
                    severity,
                    dimension,
                    summary,
                    excerpt,
                    suggestion,
                    pageNumber
            );
            risk.setHighlightRectsJson(highlightRectsJson);
            risk.setLegalReferencesJson(writeLegalReferences(node.path("legalBasis"), ragReferences));
            results.add(risk);
        }
        return results;
    }

    private String writeLegalReferences(
            JsonNode legalBasisNode,
            List<ContractRagContextBuilder.LegalReference> ragReferences
    ) {
        List<LegalReferenceDto> refs = new ArrayList<>();
        if (legalBasisNode.isArray()) {
            for (JsonNode basis : legalBasisNode) {
                String text = basis.asText("").trim();
                if (text.isBlank()) {
                    continue;
                }
                LegalReferenceDto matched = matchReference(text, ragReferences);
                if (matched != null) {
                    refs.add(matched);
                } else {
                    refs.add(new LegalReferenceDto(null, text, null, "REFERENCE"));
                }
            }
        }
        if (refs.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(refs);
        } catch (Exception ex) {
            return null;
        }
    }

    private LegalReferenceDto matchReference(
            String basisText,
            List<ContractRagContextBuilder.LegalReference> ragReferences
    ) {
        for (ContractRagContextBuilder.LegalReference ref : ragReferences) {
            if (ref.title() != null && basisText.contains(ref.title())) {
                return new LegalReferenceDto(ref.documentId(), ref.title(), ref.excerpt(), ref.docType());
            }
            if (ref.excerpt() != null && basisText.length() > 8 && ref.excerpt().contains(basisText)) {
                return new LegalReferenceDto(ref.documentId(), ref.title(), ref.excerpt(), ref.docType());
            }
            if (ref.excerpt() != null && basisText.contains(ref.excerpt().substring(0, Math.min(20, ref.excerpt().length())))) {
                return new LegalReferenceDto(ref.documentId(), ref.title(), ref.excerpt(), ref.docType());
            }
        }
        return null;
    }

    static String resolveExcerpt(String chunkContent, String rawExcerpt) {
        if (rawExcerpt == null || rawExcerpt.isBlank()) {
            return "";
        }
        String normalized = rawExcerpt.replace("…", "").trim();
        if (chunkContent.contains(normalized)) {
            return normalized;
        }
        if (chunkContent.contains(rawExcerpt.trim())) {
            return rawExcerpt.trim();
        }
        String longest = "";
        for (int len = normalized.length(); len >= 8; len--) {
            String prefix = normalized.substring(0, len);
            if (chunkContent.contains(prefix)) {
                longest = prefix;
                break;
            }
        }
        return longest;
    }

    private static String normalizeSeverity(String severity) {
        String upper = severity.toUpperCase(Locale.ROOT);
        if ("HIGH".equals(upper) || "MEDIUM".equals(upper) || "LOW".equals(upper)) {
            return upper;
        }
        return "MEDIUM";
    }

    private static String trimTo(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private String writeRects(List<HighlightRect> rects) {
        try {
            return objectMapper.writeValueAsString(rects);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String resolveOriginalFilename(DocumentEntity document) {
        String key = document.getMinioKey();
        if (key == null || key.isBlank()) {
            return document.getTitle() + ".md";
        }
        int slash = Math.max(key.lastIndexOf('/'), key.lastIndexOf('\\'));
        return slash >= 0 ? key.substring(slash + 1) : key;
    }
}
