package open.vincentf13.exchange.risk.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskLimit {

  private Long riskLimitId;
  private Long instrumentId;
  private BigDecimal initialMarginRate;
  private Integer maxLeverage;
  private BigDecimal maintenanceMarginRate;
  private BigDecimal liquidationFeeRate;
  private Boolean active;
  private Instant createdAt;
  private Instant updatedAt;
}
