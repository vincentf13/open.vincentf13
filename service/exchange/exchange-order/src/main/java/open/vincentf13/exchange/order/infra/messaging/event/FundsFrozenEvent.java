package open.vincentf13.exchange.order.infra.messaging.event;

import java.math.BigDecimal;

/**
 * Emitted by ledger when funds for an order are successfully frozen.
 */
public record FundsFrozenEvent(
        Long orderId,
        Long userId,
        String asset,
        BigDecimal frozenAmount
) {
}
