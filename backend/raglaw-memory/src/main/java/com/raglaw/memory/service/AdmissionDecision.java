package com.raglaw.memory.service;

public record AdmissionDecision(boolean accepted, String reason) {
    public static AdmissionDecision accept(String reason) { return new AdmissionDecision(true, reason); }
    public static AdmissionDecision reject(String reason) { return new AdmissionDecision(false, reason); }
}
