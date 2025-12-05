package open.vincentf13.exchange.market.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.market.sdk.rest.api.dto.OrderBookLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderBookSnapshot {
    
    private Long instrumentId;
    private List<OrderBookLevel> bids;
    private List<OrderBookLevel> asks;
    private BigDecimal bestBid;
    private BigDecimal bestAsk;
    private BigDecimal midPrice;
    private Instant updatedAt;
    
}
