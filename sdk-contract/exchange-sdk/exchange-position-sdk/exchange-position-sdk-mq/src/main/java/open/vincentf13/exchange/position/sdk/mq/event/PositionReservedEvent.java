package open.vincentf13.exchange.position.sdk.mq.event;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;

import java.math.BigDecimal;
import java.time.Instant;

public record PositionReservedEvent(
        @NotNull Long orderId,
        @NotNull Long userId,
        @NotNull Long instrumentId,
        @NotNull PositionIntentType intentType,
        @NotNull @DecimalMin(value = "0.00000001") BigDecimal reservedQuantity,
        @NotNull BigDecimal avgOpenPrice,
        @NotNull Instant reservedAt
) {
}
