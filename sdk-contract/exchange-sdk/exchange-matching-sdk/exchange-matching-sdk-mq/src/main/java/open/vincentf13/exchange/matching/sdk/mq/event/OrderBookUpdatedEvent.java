package open.vincentf13.exchange.matching.sdk.mq.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderBookUpdatedEvent(
        Long instrumentId,
        List<OrderBookLevel> bids,
        List<OrderBookLevel> asks,
        BigDecimal bestBid,
        BigDecimal bestAsk,
        BigDecimal midPrice,
        Instant updatedAt
) {
    public record OrderBookLevel(
            BigDecimal price,
            BigDecimal quantity
    ) {
    }
}
