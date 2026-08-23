package com.raglaw.rag.contract;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ContractClassifier {

    private static final Map<String, Set<String>> DOMAIN_KEYWORDS = Map.of(
            "CRIMINAL", Set.of("刑事", "犯罪", "刑罚", "看守所"),
            "ADMIN", Set.of("行政", "政府采购", "招标", "行政机关"),
            "LITIGATION", Set.of("诉讼", "仲裁", "争议解决", "管辖法院"),
            "CIVIL", Set.of("租赁", "借款", "买卖", "担保", "违约", "股权转让", "合伙")
    );

    public ClassificationResult classify(String text) {
        String normalized = text == null ? "" : text;
        String domain = "GENERAL";
        int bestScore = 0;
        for (Map.Entry<String, Set<String>> entry : DOMAIN_KEYWORDS.entrySet()) {
            int score = 0;
            for (String keyword : entry.getValue()) {
                if (normalized.contains(keyword)) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                domain = entry.getKey();
            }
        }
        String agentCode = "CONTRACT_" + domain;
        return new ClassificationResult(domain, agentCode, bestScore);
    }

    public record ClassificationResult(String domain, String suggestedAgentCode, int keywordHits) {
        public String toMetadataJson() {
            return String.format(Locale.ROOT,
                    "{\"contractDomain\":\"%s\",\"suggestedAgentCode\":\"%s\",\"keywordHits\":%d}",
                    domain, suggestedAgentCode, keywordHits);
        }
    }
}
