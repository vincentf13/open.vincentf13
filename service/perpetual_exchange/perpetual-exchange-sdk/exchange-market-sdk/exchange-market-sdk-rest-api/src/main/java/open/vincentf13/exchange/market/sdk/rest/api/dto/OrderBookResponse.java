package open.vincentf13.exchange.market.sdk.rest.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OrderBookResponse {
  Long instrumentId;
  List<OrderBookLevel> bids;
  List<OrderBookLevel> asks;
  BigDecimal bestBid;
  BigDecimal bestAsk;
  BigDecimal midPrice;
  Instant updatedAt;
}
