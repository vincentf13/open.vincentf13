package open.vincentf13.exchange.market.mq.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event emitted whenever market-data recalculates the latest mark price for an instrument.
 */
public record MarkPriceUpdatedEvent(
        Long instrumentId,
        BigDecimal markPrice,
        Long tradeId,
        Instant tradeExecutedAt,
        Instant calculatedAt
) {
}
