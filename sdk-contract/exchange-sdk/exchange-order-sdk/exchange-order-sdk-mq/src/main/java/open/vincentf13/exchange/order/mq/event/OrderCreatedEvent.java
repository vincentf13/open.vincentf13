package open.vincentf13.exchange.order.mq.event;

import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderSide;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderType;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderCreatedEvent(
        Long orderId,
        Long userId,
        Long instrumentId,
        OrderSide side,
        OrderType type,
        BigDecimal price,
        BigDecimal quantity,
        String clientOrderId,
        String frozenAsset,
        BigDecimal frozenAmount,
        Instant submittedAt
) {
}
