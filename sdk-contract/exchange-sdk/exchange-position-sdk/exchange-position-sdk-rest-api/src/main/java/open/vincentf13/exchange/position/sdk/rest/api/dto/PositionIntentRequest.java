package open.vincentf13.exchange.position.sdk.rest.api.dto;

import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderSide;

import java.math.BigDecimal;

public record PositionIntentRequest(
        @NotNull Long userId,
        @NotNull Long instrumentId,
        @NotNull OrderSide orderSide,
        @NotNull BigDecimal quantity
) { }
