package open.vincentf13.exchange.marketdata.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain entity describing the latest mark price derived from recent trades.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkPriceSnapshot {

    private Long snapshotId;
    private Long instrumentId;
    private BigDecimal markPrice;
    private Long tradeId;
    private Instant tradeExecutedAt;
    private Instant calculatedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
