package com.raglaw.agentscope.routing;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Conservative, permission-independent hard evidence checks. */
public final class DeterministicRulePrecheck implements RulePrecheck {
    private static final Pattern IMMINENT_DEADLINE = Pattern.compile(
            "(?:(?:明天|今天|后天|within\\s+\\d+\\s+days?|tomorrow|today|imminent|immediately).*(?:期限|截止|到期|deadline|due date|arbitration)|(?:期限|截止|到期|deadline|due date|arbitration).*(?:明天|今天|后天|within\\s+\\d+\\s+days?|tomorrow|today|imminent))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EXTERNAL_ACTION = Pattern.compile(
            "(?:帮我|请帮我|替我|直接|代为|请你).{0,12}(?:发送|发给|联系|提交|起诉|file|send|contact)|(?:发送律师函|提交仲裁|提起诉讼|send\\s+(?:a\\s+)?legal\\s+(?:letter|notice)|file\\s+(?:a\\s+)?lawsuit)",
            Pattern.CASE_INSENSITIVE);

    @Override
    public RulePrecheckResult evaluate(RoutingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        String query = request.query() == null ? "" : request.query();
        String normalized = query.toLowerCase(Locale.ROOT);
        Set<RiskSignal> signals = EnumSet.noneOf(RiskSignal.class);
        List<String> reasons = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        if (IMMINENT_DEADLINE.matcher(normalized).find()) {
            signals.add(RiskSignal.IMMINENT_DEADLINE);
            reasons.add("hard signal IMMINENT_DEADLINE: request references an imminent legal deadline");
        }
        if (EXTERNAL_ACTION.matcher(normalized).find()) {
            signals.add(RiskSignal.EXTERNAL_ACTION_REQUEST);
            reasons.add("hard signal EXTERNAL_ACTION_REQUEST: request asks the system to act externally");
        }
        if (normalized.matches(".*(?:刑事|犯罪|被捕|逮捕|拘留|刑事责任|criminal|arrest|prosecution|prosecuted).*") ) {
            signals.add(RiskSignal.CRIMINAL_EXPOSURE);
            reasons.add("hard signal CRIMINAL_EXPOSURE: request references potential criminal exposure");
        }
        if (normalized.matches(".*(?:放弃权利|放弃赔偿|签署放弃|waive(?:r)?\\s+(?:of\\s+)?rights|waive\\s+claim).*") ) {
            signals.add(RiskSignal.RIGHTS_WAIVER);
            reasons.add("hard signal RIGHTS_WAIVER: request references waiving a legal right");
        }
        if (normalized.matches(".*(?:高额|重大金额|百万|千万|high[- ]value|million-dollar|large claim).*") ) {
            signals.add(RiskSignal.HIGH_VALUE_DISPUTE);
            reasons.add("hard signal HIGH_VALUE_DISPUTE: request references a high-value dispute");
        }
        // The contract has no permission field. A future permission-denied check belongs here.
        if (!request.hasContractDocument() && normalized.matches(".*(?:合同|contract).*(?:审查|审核|review|检查).*|.*(?:审查|审核|review|检查).*(?:合同|contract).*") ) {
            signals.add(RiskSignal.MISSING_CORE_MATERIAL);
            missing.add("contract document");
            reasons.add("hard signal MISSING_CORE_MATERIAL: contract review requires a contract document");
        }
        return new RulePrecheckResult(signals, missing, reasons);
    }
}
