package com.example.exchange.riskmargin.domain;

/**
 * Domain value object representing the outcome of a risk evaluation.
 */
public record RiskDecision(boolean allowed, String reason) {

    public static RiskDecision allowed() {
        return new RiskDecision(true, "OK");
    }

    public static RiskDecision denied(String reason) {
        return new RiskDecision(false, reason);
    }

    public boolean isAllowed() {
        return allowed;
    }
}
