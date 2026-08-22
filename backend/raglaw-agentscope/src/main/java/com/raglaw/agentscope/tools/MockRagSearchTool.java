package com.raglaw.agentscope.tools;

import com.raglaw.rag.tool.RagSearchHit;
import com.raglaw.rag.tool.RagSearchTool;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class MockRagSearchTool implements RagSearchTool {

    @Override
    public List<RagSearchHit> search(String query, List<String> knowledgeScopes, int limit) {
        return List.of();
    }
}
