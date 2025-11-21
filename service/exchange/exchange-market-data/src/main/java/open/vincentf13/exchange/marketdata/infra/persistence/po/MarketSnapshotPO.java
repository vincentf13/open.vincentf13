package open.vincentf13.exchange.marketdata.infra.persistence.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketSnapshotPO {
    private Long snapshotId;
    private Long instrumentId;
    private BigDecimal bestBidPrice;
    private BigDecimal bestAskPrice;
    private String bidDepth;
    private String askDepth;
    private BigDecimal lastPrice;
    private BigDecimal volume24h;
    private BigDecimal turnover24h;
    private BigDecimal high24h;
    private BigDecimal low24h;
    private BigDecimal open24h;
    private BigDecimal priceChange24h;
    private BigDecimal priceChangePct;
    private BigDecimal markPrice;
    private BigDecimal indexPrice;
    private BigDecimal fundingRate;
    private BigDecimal openInterest;
    private Instant nextFundingTime;
    private Integer tradesCount24h;
    private Instant capturedAt;
    private Instant createdAt;
}
