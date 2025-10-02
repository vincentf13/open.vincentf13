package open.vincentf13.riskmargin.domain;

import java.util.Objects;

/**
 * Domain value object representing the outcome of a risk evaluation.
 */
public final class RiskDecision {

    private static final RiskDecision ALLOWED = new RiskDecision(true, "OK");

    private final boolean allowed;
    private final String reason;

    private RiskDecision(boolean allowed, String reason) {
        this.allowed = allowed;
        this.reason = Objects.requireNonNullElse(reason, "unknown");
    }

    public static RiskDecision allowed() {
        return ALLOWED;
    }

    public static RiskDecision denied(String reason) {
        return new RiskDecision(false, reason);
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String reason() {
        return reason;
    }
}
