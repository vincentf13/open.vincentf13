package open.vincentf13.exchange.marketdata.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain representation of a single K-line bucket aggregated within a fixed period.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KlineBucket {

    private Long bucketId;
    private Long instrumentId;
    /**
     * Period identifier such as 1m/5m/1h/1d.
     */
    private String period;
    private Instant bucketStart;
    private Instant bucketEnd;

    private BigDecimal openPrice;
    private BigDecimal highPrice;
    private BigDecimal lowPrice;
    private BigDecimal closePrice;

    private BigDecimal volume;
    private BigDecimal turnover;
    private Integer tradeCount;
    private BigDecimal takerBuyVolume;
    private BigDecimal takerBuyTurnover;

    private Boolean closed;
    private Instant createdAt;
    private Instant updatedAt;
}
