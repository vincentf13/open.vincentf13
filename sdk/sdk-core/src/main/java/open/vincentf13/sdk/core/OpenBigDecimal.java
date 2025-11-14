package open.vincentf13.sdk.core;

import java.math.BigDecimal;

/**
 * Common BigDecimal helpers shared across services.
 */
public final class OpenBigDecimal {

    private OpenBigDecimal() {
    }

    public static boolean isNonPositive(BigDecimal value) {
        return value == null || value.signum() <= 0;
    }

    public static BigDecimal normalizeDecimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }
}
