package open.vincentf13.exchange.account.ledger.sdk.mq.event;

import java.math.BigDecimal;

public record FundsFrozenEvent(
        Long orderId,
        Long userId,
        String asset,
        BigDecimal frozenAmount
) {
}
