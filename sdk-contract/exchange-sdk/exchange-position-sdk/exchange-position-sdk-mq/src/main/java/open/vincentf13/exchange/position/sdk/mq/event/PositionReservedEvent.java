package open.vincentf13.exchange.position.sdk.mq.event;

import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionIntentType;

import java.math.BigDecimal;
import java.time.Instant;

public record PositionReservedEvent(
        Long orderId,
        Long userId,
        Long instrumentId,
        PositionIntentType intentType,
        BigDecimal reservedQuantity,
        Instant reservedAt
) { }
