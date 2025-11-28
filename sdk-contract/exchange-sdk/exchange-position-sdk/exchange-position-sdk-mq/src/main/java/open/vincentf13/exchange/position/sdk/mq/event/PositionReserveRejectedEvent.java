package open.vincentf13.exchange.position.sdk.mq.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;

import java.time.Instant;

public record PositionReserveRejectedEvent(
        @NotNull Long orderId,
        @NotNull Long userId,
        @NotNull Long instrumentId,
        @NotNull PositionIntentType intentType,
        @NotBlank String reason,
        @NotNull Instant rejectedAt
) {
}
