package open.vincentf13.exchange.order.mq.event;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;

import java.math.BigDecimal;
import java.time.Instant;

public record PositionReserveRequestedEvent(
        @NotNull Long orderId,
        @NotNull Long userId,
        @NotNull Long instrumentId,
        @NotNull OrderSide orderSide,
        @NotNull PositionIntentType intentType,
        @NotNull @DecimalMin(value = ValidationConstant.Names.QUANTITY_MIN) BigDecimal quantity,
        @NotNull Instant requestedAt
) { }
