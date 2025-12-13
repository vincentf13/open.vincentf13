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
        @NotNull Long userId,
        @NotNull Long instrumentId,
        String clientOrderId,
        @NotNull OrderSide side,
        @NotNull OrderType type,
        BigDecimal price,
        @NotNull @DecimalMin(value = ValidationConstant.Names.QUANTITY_MIN) BigDecimal quantity,
        PositionIntentType intent,
        @NotNull BigDecimal filledQuantity,
        @NotNull BigDecimal remainingQuantity,
        BigDecimal avgFillPrice,
        BigDecimal fee,
        @NotNull OrderStatus status,
        String rejectedReason,
        Integer version,
        @NotNull Instant createdAt,
        Instant updatedAt,
        Instant submittedAt,
        Instant filledAt,
        Instant cancelledAt
) {
}
