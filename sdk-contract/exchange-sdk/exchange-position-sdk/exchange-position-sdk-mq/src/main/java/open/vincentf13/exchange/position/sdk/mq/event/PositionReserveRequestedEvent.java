package open.vincentf13.exchange.position.sdk.mq.event;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderSide;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionIntentType;

import java.math.BigDecimal;
import java.time.Instant;

public record PositionReserveRequestedEvent(
        @NotNull Long orderId,
        @NotNull Long userId,
        @NotNull Long instrumentId,
        @NotNull OrderSide orderSide,
        @NotNull PositionIntentType intentType,
        @NotNull @DecimalMin(value = "0.00000001") BigDecimal quantity,
        @NotNull Instant requestedAt
) { }
