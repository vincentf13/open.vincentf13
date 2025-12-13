package open.vincentf13.exchange.order.sdk.rest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.OrderStatus;
import open.vincentf13.exchange.common.sdk.enums.OrderType;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        @NotNull Long orderId,
        String clientOrderId,
        @NotNull Long userId,
        @NotNull Long instrumentId,
        @NotNull OrderSide side,
        @NotNull PositionIntentType intent,
        @NotNull OrderType type,
        @NotNull OrderStatus status,
        @DecimalMin(value = ValidationConstant.Names.PRICE_MIN) BigDecimal price,
        @NotNull @DecimalMin(value = ValidationConstant.Names.QUANTITY_MIN) BigDecimal quantity,
        @NotNull BigDecimal filledQuantity,
        @NotNull BigDecimal remainingQuantity,
        BigDecimal avgFillPrice,
        @NotNull @DecimalMin(value = ValidationConstant.Names.FEE_MIN, inclusive = true) BigDecimal fee,
        String rejectedReason,
        Integer version,
        @NotNull Instant createdAt,
        Instant updatedAt
) {
}
