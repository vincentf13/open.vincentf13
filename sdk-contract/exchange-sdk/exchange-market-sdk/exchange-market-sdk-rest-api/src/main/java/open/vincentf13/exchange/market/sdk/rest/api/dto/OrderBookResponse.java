package open.vincentf13.exchange.market.sdk.rest.api.dto;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Value
@Builder
public class OrderBookResponse {
    Long instrumentId;
    @Singular("bid")
    List<OrderBookLevel> bids;
    @Singular("ask")
    List<OrderBookLevel> asks;
    BigDecimal bestBid;
    BigDecimal bestAsk;
    BigDecimal midPrice;
    Instant updatedAt;
}
