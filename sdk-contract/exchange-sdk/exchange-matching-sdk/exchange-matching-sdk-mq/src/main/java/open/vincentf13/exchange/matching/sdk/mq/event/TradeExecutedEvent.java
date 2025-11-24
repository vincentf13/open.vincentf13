package open.vincentf13.exchange.matching.sdk.mq.event;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeExecutedEvent(
        @NotNull Long tradeId,
        @NotNull Long orderId,
        @NotNull Long instrumentId,
        @NotBlank String quoteAsset,
        @org.jetbrains.annotations.NotNull @DecimalMin(value = "0.00000001") BigDecimal price,
        @NotNull @DecimalMin(value = "0.00000001") BigDecimal quantity,
        @NotNull @DecimalMin(value = "0.00000000") BigDecimal fee,
        @NotNull Instant executedAt
) {
}
