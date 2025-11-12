package open.vincentf13.exchange.position.sdk.mq.event;

import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionIntentType;

import java.time.Instant;

public record PositionReserveRejectedEvent(
        Long orderId,
        Long userId,
        Long instrumentId,
        PositionIntentType intentType,
        String reason,
        Instant rejectedAt
) { }
