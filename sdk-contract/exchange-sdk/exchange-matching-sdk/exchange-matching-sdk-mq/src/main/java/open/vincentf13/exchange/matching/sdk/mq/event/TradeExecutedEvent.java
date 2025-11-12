package open.vincentf13.exchange.matching.sdk.mq.event;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeExecutedEvent(
        Long tradeId,
        Long orderId,
        Long instrumentId,
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal fee,
        Instant executedAt
) {
}
