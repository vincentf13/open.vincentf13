package open.vincentf13.exchange.risk.domain.model;

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
public class RiskSnapshot {

    private Long snapshotId;
    private Long userId;
    private Long accountId;
    private Long instrumentId;
    private Long positionId;
    private BigDecimal maintenanceMarginRate;
    private BigDecimal notionalValue;
    private BigDecimal usedMargin;
    private BigDecimal equity;
    private BigDecimal marginRatio;
    private BigDecimal liquidationPrice;
    private String status;
    private Long riskVersion;
    private String snapshotSource;
    private Instant snapshotAt;
    private Instant createdAt;
    private Instant updatedAt;
}
