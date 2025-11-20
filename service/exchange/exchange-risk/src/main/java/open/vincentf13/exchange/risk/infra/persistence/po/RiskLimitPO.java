package open.vincentf13.exchange.risk.infra.persistence.po;

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
public class RiskLimitPO {

    private Long riskLimitId;
    private Long instrumentId;
    private BigDecimal initialMarginRate;
    private Integer maxLeverage;
    private BigDecimal maintenanceMarginRate;
    private BigDecimal liquidationFeeRate;
    private BigDecimal positionSizeMin;
    private BigDecimal positionSizeMax;
    private BigDecimal maxPositionValue;
    private BigDecimal maxOrderValue;
    private Boolean active;
    private Instant effectiveFrom;
    private Instant effectiveTo;
    private Instant createdAt;
    private Instant updatedAt;
}
