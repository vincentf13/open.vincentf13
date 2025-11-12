package open.vincentf13.exchange.order.messaging;

import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderSide;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderStatus;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderTimeInForce;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderType;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderSubmittedEvent(
        Long orderId,
        Long userId,
        Long instrumentId,
        OrderSide side,
        OrderType type,
        OrderStatus status,
        OrderTimeInForce timeInForce,
        BigDecimal price,
        BigDecimal stopPrice,
        BigDecimal quantity,
        String clientOrderId,
        String source,
        Instant createdAt
) {
}
