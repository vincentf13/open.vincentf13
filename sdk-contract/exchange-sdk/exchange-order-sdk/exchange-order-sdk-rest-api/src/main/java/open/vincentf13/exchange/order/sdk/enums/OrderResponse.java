package open.vincentf13.exchange.order.sdk.enums;

import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.OrderStatus;
import open.vincentf13.exchange.common.sdk.enums.OrderType;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        Long orderId,
        String clientOrderId,
        Long userId,
        Long instrumentId,
        OrderSide side,
        PositionIntentType intent,
        BigDecimal closeCostPrice,
        OrderType type,
        OrderStatus status,
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal filledQuantity,
        BigDecimal remainingQuantity,
        BigDecimal avgFillPrice,
        BigDecimal fee,
        Integer version,
        Instant createdAt,
        Instant updatedAt
) { }
