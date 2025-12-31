package open.vincentf13.exchange.matching.sdk.mq.event;

import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.common.sdk.model.OrderUpdate;

import java.time.Instant;
import java.util.List;

public record OrderBookUpdatedEvent(
        @NotNull Long instrumentId,
        List<OrderUpdate> updates,
        @NotNull Instant updatedAt
) {
}
