package open.vincentf13.exchange.order.sdk.rest.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        Long orderId,
        String clientOrderId,
        Long userId,
        Long instrumentId,
        OrderSide side,
        OrderType type,
        OrderStatus status,
        OrderTimeInForce timeInForce,
        BigDecimal price,
        BigDecimal stopPrice,
        BigDecimal quantity,
        BigDecimal filledQuantity,
        BigDecimal remainingQuantity,
        BigDecimal avgFillPrice,
        BigDecimal fee,
        String source,
        Integer version,
        Instant createdAt,
        Instant updatedAt
) { }
