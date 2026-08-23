package com.raglaw.agentscope.a2a;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class QuestionRecommender {

    private static final List<String> DEFAULT_FOLLOW_UPS = List.of(
            "相关法规的适用条件是什么？",
            "类似案例的裁判要点有哪些？",
            "如需起诉应准备哪些材料？"
    );

    public List<String> recommend(String userMessage, String agentCode, int limit) {
        if (userMessage == null || userMessage.isBlank()) {
            return DEFAULT_FOLLOW_UPS.stream().limit(limit).toList();
        }
        if (userMessage.contains("工资") || userMessage.contains("劳动")) {
            return List.of(
                    "拖欠工资可以主张哪些赔偿？",
                    "劳动仲裁的时效是多久？",
                    "如何固定欠薪证据？"
            ).stream().limit(limit).toList();
        }
        if (userMessage.contains("合同") || userMessage.contains("违约")) {
            return List.of(
                    "违约金过高能否请求调减？",
                    "解除合同需要满足哪些条件？",
                    "对方违约时如何保全证据？"
            ).stream().limit(limit).toList();
        }
        return DEFAULT_FOLLOW_UPS.stream().limit(limit).toList();
    }
}
