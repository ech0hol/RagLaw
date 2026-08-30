package com.raglaw.rag.contract;

import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ContractClassifier {

    public ClassificationResult classify(String text) {
        return new ClassificationResult("GENERAL", "CONTRACT", 0);
    }

    public record ClassificationResult(String domain, String suggestedAgentCode, int keywordHits) {
        public String toMetadataJson() {
            return String.format(Locale.ROOT,
                    "{\"contractDomain\":\"%s\",\"suggestedAgentCode\":\"%s\",\"keywordHits\":%d}",
                    domain, suggestedAgentCode, keywordHits);
        }
    }
}
