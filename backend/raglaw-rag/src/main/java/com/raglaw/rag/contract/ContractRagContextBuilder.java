package com.raglaw.rag.contract;

import com.raglaw.rag.config.RagProperties;
import com.raglaw.rag.domain.DocumentChunkEntity;
import com.raglaw.rag.domain.DocumentEntity;
import com.raglaw.rag.tool.HybridRagSearchTool;
import com.raglaw.rag.tool.RagSearchHit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ContractRagContextBuilder {

    private static final int BATCH_QUERY_PREVIEW_CHARS = 800;
    private static final int BATCH_CHUNK_COUNT = 8;

    private final HybridRagSearchTool hybridRagSearchTool;
    private final ContractKnowledgeScopeMapper scopeMapper;
    private final RagProperties ragProperties;

    public ContractRagContextBuilder(
            HybridRagSearchTool hybridRagSearchTool,
            ContractKnowledgeScopeMapper scopeMapper,
            RagProperties ragProperties
    ) {
        this.hybridRagSearchTool = hybridRagSearchTool;
        this.scopeMapper = scopeMapper;
        this.ragProperties = ragProperties;
    }

    public ContractRagContext build(DocumentEntity document, String suggestedAgentCode, String contractTextPreview) {
        return buildForBatch(document, suggestedAgentCode, null, contractTextPreview);
    }

    public ContractRagContext buildForBatch(
            DocumentEntity document,
            String suggestedAgentCode,
            List<DocumentChunkEntity> batchChunks,
            String contractTextPreview
    ) {
        List<String> scopes = scopeMapper.scopesForAgent(suggestedAgentCode);
        String query = buildQuery(document, batchChunks, contractTextPreview);
        int limit = ragProperties.getContract().getRagHitLimit();
        List<RagSearchHit> hits = hybridRagSearchTool.searchDetailed(
                query,
                scopes,
                limit,
                suggestedAgentCode,
                document.getId(),
                false,
                "contract_review"
        ).hits();

        List<LegalReference> references = new ArrayList<>();
        StringBuilder prompt = new StringBuilder();
        if (hits.isEmpty()) {
            prompt.append("（知识库未检索到相关法规或案例，请仅基于合同正文分析。）\n");
        } else {
            prompt.append("以下是与本合同条款相关的法规与案例摘录，分析时请优先引用：\n");
            for (RagSearchHit hit : hits) {
                String docType = inferDocType(hit.l1L2L3Path());
                references.add(new LegalReference(
                        hit.documentId(),
                        hit.title() != null ? hit.title() : "未命名文档",
                        hit.llmContentOrExcerpt(),
                        docType
                ));
                prompt.append("[")
                        .append(docType)
                        .append("] ")
                        .append(hit.title() != null ? hit.title() : "未命名")
                        .append(" / ")
                        .append(hit.llmContentOrExcerpt())
                        .append("\n");
            }
        }
        return new ContractRagContext(prompt.toString(), references, hits.size(), hits);
    }

    private static String buildQuery(
            DocumentEntity document,
            List<DocumentChunkEntity> batchChunks,
            String contractTextPreview
    ) {
        String title = document.getTitle() != null ? document.getTitle() : "合同";
        if (batchChunks != null && !batchChunks.isEmpty()) {
            StringBuilder batchText = new StringBuilder();
            int count = 0;
            for (DocumentChunkEntity chunk : batchChunks) {
                if (chunk.getContent() == null || chunk.getContent().isBlank()) {
                    continue;
                }
                if (count >= BATCH_CHUNK_COUNT) {
                    break;
                }
                batchText.append(chunk.getContent()).append('\n');
                count++;
            }
            String excerpt = batchText.toString().trim();
            if (excerpt.length() > BATCH_QUERY_PREVIEW_CHARS) {
                excerpt = excerpt.substring(0, BATCH_QUERY_PREVIEW_CHARS);
            }
            return title + " 合同条款审查 " + excerpt;
        }
        String preview = contractTextPreview != null ? contractTextPreview : "";
        if (preview.length() > 500) {
            preview = preview.substring(0, 500);
        }
        return title + " 合同审查 " + preview;
    }

    static String inferDocType(String path) {
        if (path == null || path.isBlank()) {
            return "REFERENCE";
        }
        if (path.startsWith("/STATUTE") || path.contains("/STATUTE/")) {
            return "STATUTE";
        }
        if (path.startsWith("/CASE") || path.contains("/CASE/")) {
            return "CASE";
        }
        if (path.startsWith("/CONTRACT") || path.contains("/CONTRACT/")) {
            return "CONTRACT";
        }
        return "REFERENCE";
    }

    public record ContractRagContext(
            String promptBlock,
            List<LegalReference> references,
            int hitCount,
            List<RagSearchHit> hits
    ) {
    }

    public record LegalReference(String documentId, String title, String excerpt, String docType) {
    }
}