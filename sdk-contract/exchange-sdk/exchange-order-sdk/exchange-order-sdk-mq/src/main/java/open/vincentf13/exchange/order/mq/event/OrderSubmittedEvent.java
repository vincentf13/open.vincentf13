package open.vincentf13.exchange.order.mq.event;

import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderSide;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderStatus;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderTimeInForce;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderType;

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
