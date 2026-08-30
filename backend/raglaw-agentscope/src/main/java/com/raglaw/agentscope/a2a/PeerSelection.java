package com.raglaw.agentscope.a2a;

public record PeerSelection(String peerCode, String reason, boolean usedLlm, boolean lowConfidence) {

    public PeerSelection(String peerCode, String reason, boolean usedLlm) {
        this(peerCode, reason, usedLlm, false);
    }
}
