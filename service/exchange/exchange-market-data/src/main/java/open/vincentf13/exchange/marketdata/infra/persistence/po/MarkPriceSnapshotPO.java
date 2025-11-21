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
public class MarkPriceSnapshotPO {
    private Long snapshotId;
    private Long instrumentId;
    private BigDecimal markPrice;
    private Long tradeId;
    private Instant tradeExecutedAt;
    private Instant calculatedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
