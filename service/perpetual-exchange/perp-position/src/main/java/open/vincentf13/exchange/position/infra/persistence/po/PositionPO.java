package open.vincentf13.exchange.position.infra.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.common.sdk.enums.PositionSide;
import open.vincentf13.exchange.common.sdk.enums.PositionStatus;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("positions")
public class PositionPO {

  @TableId(value = "position_id", type = IdType.INPUT)
  private Long positionId;

  private Long userId;
  private Long instrumentId;
  private Integer leverage;
  private BigDecimal margin;
  private PositionSide side;
  private BigDecimal entryPrice;
  private BigDecimal quantity;
  private BigDecimal closingReservedQuantity;
  private BigDecimal markPrice;
  private BigDecimal marginRatio;
  private BigDecimal unrealizedPnl;
  private BigDecimal liquidationPrice;
  private BigDecimal cumRealizedPnl;
  private BigDecimal cumFee;
  private BigDecimal cumFundingFee;

  private PositionStatus status;
  private Integer version;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant closedAt;
}
