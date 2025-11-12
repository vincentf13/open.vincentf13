package open.vincentf13.exchange.position.sdk.mq.event;

import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderSide;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentType;

import java.math.BigDecimal;
import java.time.Instant;

public record PositionReserveRequestedEvent(
        Long orderId,
        Long userId,
        Long instrumentId,
        OrderSide orderSide,
        PositionIntentType intentType,
        BigDecimal quantity,
        Instant requestedAt
) { }
