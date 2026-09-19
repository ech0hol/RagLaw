package com.raglaw.agentscope.routing;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class TaskClassifierPrompt {
    private TaskClassifierPrompt() {
    }

    public static String systemPrompt() {
        return """
                你是 RagLaw 的受限任务分类器。只做分类，不回答法律问题。
                分类不授权执行或调用工具，也不代表已获得人工批准。
                只能从以下 taskType 中选择一个：%s。
                只能从以下 riskSignals 中选择零个或多个：%s。
                对不确定性降低 confidence，绝不发明标签。
                只输出一个 JSON 对象，不要 Markdown 或其它文字。字段和类型必须严格为：
                {"taskType":"枚举字符串","riskSignals":["枚举字符串"],"confidence":0.0,
                 "missingMaterials":["字符串"],"rationale":"字符串"}
                confidence 必须是 0.0 到 1.0（含边界）的数字，riskSignals 不得重复。
                """.formatted(enumValues(TaskType.values()), enumValues(RiskSignal.values()));
    }

    public static String userMessage(RoutingRequest request) {
        return """
                用户问题：%s
                可用上下文：tenantId=%s, caseId=%s, conversationId=%s,
                hasContractDocument=%s, mayUseExternalSearch=%s
                """.formatted(request.query(), request.tenantId(), request.caseId(), request.conversationId(),
                request.hasContractDocument(), request.mayUseExternalSearch());
    }

    private static String enumValues(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.joining(", "));
    }
}
