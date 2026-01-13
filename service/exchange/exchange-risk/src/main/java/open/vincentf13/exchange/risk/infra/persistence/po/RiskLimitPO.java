package open.vincentf13.exchange.risk.infra.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("risk_limits")
public class RiskLimitPO {

  @TableId(type = IdType.INPUT)
  private Long id;

  private Long instrumentId;
  private BigDecimal initialMarginRate;
  private Integer maxLeverage;
  private BigDecimal maintenanceMarginRate;
  private BigDecimal liquidationFeeRate;
  private Boolean isActive;
  private Instant createdAt;
  private Instant updatedAt;
}
