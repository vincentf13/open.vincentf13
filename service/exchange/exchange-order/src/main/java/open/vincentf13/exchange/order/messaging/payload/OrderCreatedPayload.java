package open.vincentf13.exchange.order.messaging.payload;

import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderSide;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderTimeInForce;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderType;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderCreatedPayload(
        Long orderId,
        Long userId,
        Long instrumentId,
        OrderSide side,
        OrderType type,
        OrderTimeInForce timeInForce,
        BigDecimal price,
        BigDecimal stopPrice,
        BigDecimal quantity,
        String source,
        String clientOrderId,
        Instant createdAt
) {
}
