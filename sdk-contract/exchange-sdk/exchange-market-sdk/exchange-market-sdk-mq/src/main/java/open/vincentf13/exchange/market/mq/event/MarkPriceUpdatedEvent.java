package open.vincentf13.exchange.market.mq.event;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event emitted whenever market-data recalculates the latest mark price for an instrument.
 */
public record MarkPriceUpdatedEvent(
        @NotNull Long instrumentId,
        @NotNull @DecimalMin(value = "0.00000001") BigDecimal markPrice,
        @NotNull Long tradeId,
        @NotNull Instant tradeExecutedAt,
        @NotNull Instant calculatedAt
) {
}
