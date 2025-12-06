package open.vincentf13.exchange.position.sdk.mq.event;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.PositionSide;

import java.math.BigDecimal;
import java.time.Instant;

public record PositionReservedEvent(
        @NotNull Long orderId,
        @NotNull Long userId,
        @NotNull Long instrumentId,
        @NotNull PositionSide side,
        @NotNull @DecimalMin(value = ValidationConstant.Names.QUANTITY_MIN, inclusive = true) BigDecimal reservedQuantity,
        @NotNull @DecimalMin(value = ValidationConstant.Names.PRICE_MIN, inclusive = true) BigDecimal closingEntryPrice,
        @NotNull Instant reservedAt
) {
}
