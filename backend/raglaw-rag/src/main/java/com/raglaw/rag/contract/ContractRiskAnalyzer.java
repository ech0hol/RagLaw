package com.raglaw.rag.contract;

import com.raglaw.common.util.Ids;
import com.raglaw.rag.domain.ContractRiskEntity;
import com.raglaw.rag.domain.DocumentChunkEntity;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.ingest.PdfPageLocator;
import com.raglaw.rag.repository.ContractRiskRepository;
import com.raglaw.rag.repository.DocumentChunkRepository;
import com.raglaw.rag.repository.DocumentRepository;
import com.raglaw.rag.service.storage.DocumentStorageService;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContractRiskAnalyzer {

    private static final List<RiskRule> RULES = List.of(
            new RiskRule(Pattern.compile("免责|免除.*责任"), "HIGH", "免责条款", "存在免责或限责表述", "建议明确免责范围并评估是否显失公平。"),
            new RiskRule(Pattern.compile("单方解除|任意解除"), "HIGH", "解除权", "存在单方解除权约定", "建议补充解除条件、通知期限与违约责任。"),
            new RiskRule(Pattern.compile("违约金|滞纳金"), "MEDIUM", "违约责任", "涉及违约金或滞纳金", "建议核对违约金比例是否过高，并约定计算方式。"),
            new RiskRule(Pattern.compile("管辖|仲裁|争议解决"), "MEDIUM", "争议解决", "涉及管辖或仲裁条款", "建议确认管辖法院/仲裁机构是否对己方有利。"),
            new RiskRule(Pattern.compile("无限责任|连带保证|连带责任"), "HIGH", "担保责任", "存在较重担保或连带责任", "建议评估担保范围与追偿安排。"),
            new RiskRule(Pattern.compile("保密|竞业限制"), "LOW", "保密义务", "涉及保密或竞业限制", "建议明确保密期限、范围与违约责任。")
    );

    private final DocumentChunkRepository chunkRepository;
    private final ContractRiskRepository riskRepository;
    private final DocumentRepository documentRepository;
    private final DocumentStorageService documentStorageService;
    private final PdfPageLocator pdfPageLocator;

    public ContractRiskAnalyzer(
            DocumentChunkRepository chunkRepository,
            ContractRiskRepository riskRepository,
            DocumentRepository documentRepository,
            DocumentStorageService documentStorageService,
            PdfPageLocator pdfPageLocator
    ) {
        this.chunkRepository = chunkRepository;
        this.riskRepository = riskRepository;
        this.documentRepository = documentRepository;
        this.documentStorageService = documentStorageService;
        this.pdfPageLocator = pdfPageLocator;
    }

    @Transactional
    public List<ContractRiskEntity> analyze(String documentId) {
        riskRepository.deleteByDocumentId(documentId);
        DocumentEntity document = documentRepository.findById(documentId).orElse(null);
        byte[] pdfBytes = null;
        boolean isPdf = false;
        if (document != null) {
            String filename = resolveOriginalFilename(document);
            isPdf = filename.toLowerCase().endsWith(".pdf");
            if (isPdf && document.getMinioKey() != null) {
                try (InputStream inputStream = documentStorageService.load(document.getMinioKey())) {
                    pdfBytes = inputStream.readAllBytes();
                } catch (Exception ignored) {
                    pdfBytes = null;
                }
            }
        }

        List<DocumentChunkEntity> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        List<ContractRiskEntity> risks = new ArrayList<>();
        for (DocumentChunkEntity chunk : chunks) {
            for (RiskRule rule : RULES) {
                if (rule.pattern().matcher(chunk.getContent()).find()) {
                    String excerpt = chunk.getContent().length() <= 200
                            ? chunk.getContent()
                            : chunk.getContent().substring(0, 200) + "…";
                    Integer pageNumber = null;
                    if (isPdf && pdfBytes != null) {
                        pageNumber = pdfPageLocator.findPage(pdfBytes, excerpt);
                    }
                    risks.add(new ContractRiskEntity(
                            Ids.newId(),
                            documentId,
                            chunk.getId(),
                            rule.severity(),
                            rule.dimension(),
                            rule.summary(),
                            excerpt,
                            rule.suggestion(),
                            pageNumber
                    ));
                }
            }
        }
        return riskRepository.saveAll(risks);
    }

    private static String resolveOriginalFilename(DocumentEntity document) {
        String key = document.getMinioKey();
        if (key == null || key.isBlank()) {
            return document.getTitle() + ".md";
        }
        int slash = Math.max(key.lastIndexOf('/'), key.lastIndexOf('\\'));
        return slash >= 0 ? key.substring(slash + 1) : key;
    }

    private record RiskRule(
            Pattern pattern,
            String severity,
            String dimension,
            String summary,
            String suggestion
    ) {
    }
}
