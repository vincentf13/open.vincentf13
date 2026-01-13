package open.vincentf13.exchange.order.infra.persistence.po;

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
import open.vincentf13.exchange.common.sdk.enums.OrderStatus;
import open.vincentf13.exchange.common.sdk.enums.OrderType;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("orders")
public class OrderPO {
  @TableId(value = "order_id", type = IdType.INPUT)
  private Long orderId;

  private Long userId;
  private Long instrumentId;
  private String clientOrderId;
  private OrderSide side;
  private OrderType type;
  private BigDecimal price;
  private BigDecimal quantity;
  private PositionIntentType intent;
  private BigDecimal filledQuantity;
  private BigDecimal remainingQuantity;
  private BigDecimal avgFillPrice;
  private BigDecimal fee;
  private OrderStatus status;
  private String rejectedReason;
  private Instant createdAt;
  private Instant updatedAt;
  private Instant submittedAt;
  private Instant filledAt;
  private Instant cancelledAt;
  private Integer version;
}
