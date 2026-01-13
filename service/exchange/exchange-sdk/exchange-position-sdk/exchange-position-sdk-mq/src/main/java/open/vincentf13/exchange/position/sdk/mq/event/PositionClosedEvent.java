package open.vincentf13.exchange.position.sdk.mq.event;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record PositionClosedEvent(
    @NotNull Long userId, @NotNull Long instrumentId, @NotNull Instant timestamp) {}
