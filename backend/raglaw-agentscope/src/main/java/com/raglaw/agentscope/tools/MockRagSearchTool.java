package com.raglaw.agentscope.tools;

import com.raglaw.rag.tool.RagSearchHit;
import com.raglaw.rag.tool.RagSearchTool;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("test")
@Primary
@ConditionalOnProperty(prefix = "raglaw.agentscope.rag", name = "mock", havingValue = "true")
@Component
public class MockRagSearchTool implements RagSearchTool {

    @Override
    public List<RagSearchHit> search(String query, List<String> knowledgeScopes, int limit, String agentCode) {
        return List.of();
    }

    @Override
    public List<RagSearchHit> searchScopedToDocument(
            String query,
            List<String> knowledgeScopes,
            int limit,
            String agentCode,
            String documentId
    ) {
        return List.of();
    }
}
