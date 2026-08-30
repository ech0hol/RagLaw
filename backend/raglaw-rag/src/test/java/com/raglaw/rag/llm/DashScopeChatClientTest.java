package com.raglaw.rag.llm;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DashScopeChatClientTest {

    @Test
    void mockMode_returnsValidJson() throws Exception {
        DashScopeChatClient client = new DashScopeChatClient(new ObjectMapper(), "", true);
        String json = client.completeJson("system", "chunkIndex=0 content=违约方应支付合同总价30%的违约金", "qwen-max");
        assertTrue(json.contains("\"risks\""));
        new ObjectMapper().readTree(json);
    }
}
