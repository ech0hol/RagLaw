package com.raglaw.agentscope.a2a;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class A2aPeerSelector {

    /** Mirrors MVP GENERAL.a2a_peers_json for tests. */
    public static final List<String> MVP_A2A_PEERS = List.of(
            "STATUTE",
            "CASE",
            "CONTRACT"
    );

    private static final List<PeerRule> RULES = List.of(
            new PeerRule("STATUTE", Set.of(
                    "劳动", "工资", "社保", "工伤", "加班", "解雇", "劳动合同", "拖欠", "维权",
                    "借贷", "买卖", "继承", "婚姻", "侵权", "民法", "物权", "违约金", "经济补偿",
                    "法规", "法条", "条例", "规定", "刑法", "行政", "宪法"
            )),
            new PeerRule("CASE", Set.of("案例", "判决", "裁判", "法院", "胜诉", "败诉", "民事案例", "刑事案例")),
            new PeerRule("CONTRACT", Set.of("合同", "违约", "条款", "协议", "租赁", "借款", "担保", "定金"))
    );

    private static final List<String> PEER_PRIORITY = List.of(
            "STATUTE",
            "CASE",
            "CONTRACT"
    );

    private static final Set<String> OUT_OF_SCOPE_KEYWORDS = Set.of(
            "化学品", "危化品", "危险化学品", "易制毒", "爆炸物", "烟花爆竹",
            "行政处罚", "行政许可", "许可证", "准购证", "毒品", "犯罪"
    );

    private final A2aRoutingLlm routingLlm;

    public A2aPeerSelector(A2aRoutingLlm routingLlm) {
        this.routingLlm = routingLlm;
    }

    public PeerSelection select(List<String> peers, String query) {
        if (peers == null || peers.isEmpty()) {
            throw new IllegalArgumentException("peers must not be empty");
        }
        String normalized = query == null ? "" : query.trim();
        Set<String> allowed = new LinkedHashSet<>();
        for (String peer : peers) {
            if (peer != null && !peer.isBlank()) {
                allowed.add(peer);
            }
        }
        if (allowed.isEmpty()) {
            throw new IllegalArgumentException("peers must not be empty");
        }

        List<String> ruleMatches = matchByRules(normalized, allowed);
        if (ruleMatches.size() == 1) {
            String peer = ruleMatches.get(0);
            return new PeerSelection(peer, "rule:" + peer, false);
        }

        if (ruleMatches.size() > 1) {
            Optional<String> llmPeer = routingLlm.pickPeer(new ArrayList<>(allowed), normalized);
            if (llmPeer.isPresent() && allowed.contains(llmPeer.get())) {
                return new PeerSelection(llmPeer.get(), "llm:ambiguous_rules", true);
            }
            String fallback = pickByPriority(ruleMatches);
            return new PeerSelection(fallback, "rule_priority:" + fallback, false);
        }

        Optional<String> llmPeer = routingLlm.pickPeer(new ArrayList<>(allowed), normalized);
        if (llmPeer.isPresent() && allowed.contains(llmPeer.get())) {
            return new PeerSelection(llmPeer.get(), "llm:no_rule_match", true);
        }

        if (containsAny(normalized, OUT_OF_SCOPE_KEYWORDS)) {
            String fallback = firstAllowed(allowed, "STATUTE_CIVIL", "STATUTE");
            return new PeerSelection(fallback, "out_of_scope:" + fallback, false, true);
        }

        String typeFallback = fallbackByType(normalized, allowed);
        return new PeerSelection(typeFallback, "type_fallback:" + typeFallback, false);
    }

    private static List<String> matchByRules(String query, Set<String> allowed) {
        LinkedHashSet<String> matches = new LinkedHashSet<>();
        for (PeerRule rule : RULES) {
            if (!allowed.contains(rule.peerCode())) {
                continue;
            }
            if (containsAny(query, rule.keywords())) {
                matches.add(rule.peerCode());
            }
        }
        return new ArrayList<>(matches);
    }

    private static String pickByPriority(List<String> candidates) {
        for (String preferred : PEER_PRIORITY) {
            if (candidates.contains(preferred)) {
                return preferred;
            }
        }
        return candidates.get(0);
    }

    private static String fallbackByType(String query, Set<String> allowed) {
        if (containsAny(query, Set.of("合同", "违约", "条款", "协议", "租赁", "借款", "担保", "定金"))) {
            return firstAllowed(allowed, "CONTRACT_GENERAL", "CONTRACT");
        }
        if (containsAny(query, Set.of("案例", "判决", "裁判", "法院", "胜诉", "败诉"))) {
            return firstAllowed(allowed, "CASE_CIVIL", "CASE");
        }
        return firstAllowed(allowed, "STATUTE_CIVIL", "STATUTE");
    }

    private static String firstAllowed(Set<String> allowed, String... preferredCodes) {
        for (String code : preferredCodes) {
            if (allowed.contains(code)) {
                return code;
            }
            String upper = code.toUpperCase(Locale.ROOT);
            for (String peer : allowed) {
                if (peer.toUpperCase(Locale.ROOT).startsWith(upper)) {
                    return peer;
                }
            }
        }
        return allowed.iterator().next();
    }

    private static boolean containsAny(String text, Set<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private record PeerRule(String peerCode, Set<String> keywords) {
    }
}
