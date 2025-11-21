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
public class KlineBucketPO {
    private Long bucketId;
    private Long instrumentId;
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
