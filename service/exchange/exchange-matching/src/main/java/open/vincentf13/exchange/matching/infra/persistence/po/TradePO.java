package open.vincentf13.exchange.matching.infra.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;
import open.vincentf13.exchange.common.sdk.enums.TradeType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("trade")
public class TradePO {
  @TableId(value = "trade_id", type = IdType.INPUT)
  private Long tradeId;

  private Long instrumentId;
  private Long makerUserId;
  private Long takerUserId;
  private Long orderId;
  private Long counterpartyOrderId;
  private BigDecimal orderQuantity;
  private BigDecimal orderFilledQuantity;
  private BigDecimal counterpartyOrderQuantity;
  private BigDecimal counterpartyOrderFilledQuantity;
  private OrderSide orderSide;
  private OrderSide counterpartyOrderSide;
  private PositionIntentType makerIntent;
  private PositionIntentType takerIntent;
  private TradeType tradeType;
  private BigDecimal price;
  private BigDecimal quantity;
  private BigDecimal totalValue;
  private BigDecimal makerFee;
  private BigDecimal takerFee;
  private Instant executedAt;
  private Instant createdAt;
}
