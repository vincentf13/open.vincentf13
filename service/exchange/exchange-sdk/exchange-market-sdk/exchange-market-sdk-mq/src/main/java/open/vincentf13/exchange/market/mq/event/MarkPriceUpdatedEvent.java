package open.vincentf13.exchange.market.mq.event;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;

import java.math.BigDecimal;
import java.time.Instant;

/**
  Event emitted whenever market service recalculates the latest mark price for an instrument.
 */
public record MarkPriceUpdatedEvent(
        @NotNull Long instrumentId,
        @NotNull @DecimalMin(value = ValidationConstant.Names.PRICE_MIN) BigDecimal markPrice,
        @NotNull Long tradeId,
        @NotNull Instant tradeExecutedAt,
        @NotNull Instant calculatedAt
) {
}
