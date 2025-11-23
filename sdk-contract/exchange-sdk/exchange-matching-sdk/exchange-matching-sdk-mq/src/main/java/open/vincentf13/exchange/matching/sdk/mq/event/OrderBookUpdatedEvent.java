package open.vincentf13.exchange.matching.sdk.mq.event;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderBookUpdatedEvent(
        @NotNull Long instrumentId,
        List<OrderBookLevel> bids,
        List<OrderBookLevel> asks,
        BigDecimal bestBid,
        BigDecimal bestAsk,
        BigDecimal midPrice,
        @NotNull Instant updatedAt
) {
    public record OrderBookLevel(
            @NotNull BigDecimal price,
            @NotNull BigDecimal quantity
    ) {
    }
}
