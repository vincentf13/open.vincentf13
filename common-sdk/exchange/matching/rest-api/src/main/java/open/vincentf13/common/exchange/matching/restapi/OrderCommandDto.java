package open.vincentf13.common.exchange.matching.restapi;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Canonical matching engine order command definition.
 */
public record OrderCommandDto(@NotNull String accountId,
                              @NotNull String symbol,
                              @NotNull OrderSide side,
                              @NotNull BigDecimal quantity,
                              BigDecimal price) {

    public enum OrderSide {
        BUY,
        SELL
    }
}
