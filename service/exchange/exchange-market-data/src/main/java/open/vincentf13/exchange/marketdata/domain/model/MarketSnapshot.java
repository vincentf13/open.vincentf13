package open.vincentf13.exchange.marketdata.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Real-time market snapshot aggregated from orderbook and trade statistics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketSnapshot {

    private Long snapshotId;
    private Long instrumentId;

    private BigDecimal lastPrice;
    private BigDecimal volume24h;
    private BigDecimal turnover24h;
    private BigDecimal high24h;
    private BigDecimal low24h;
    private BigDecimal open24h;
    private BigDecimal priceChange24h;
    private BigDecimal priceChangePct;
    private Instant capturedAt;
    private Instant createdAt;
}
