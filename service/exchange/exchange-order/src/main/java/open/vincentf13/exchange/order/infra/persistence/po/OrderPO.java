package open.vincentf13.exchange.order.infra.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.OrderStatus;
import open.vincentf13.exchange.common.sdk.enums.OrderType;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;

import java.math.BigDecimal;
import java.time.Instant;

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
    private PositionIntentType intent;
    private OrderType type;
    private OrderStatus status;
    private BigDecimal price;
    private BigDecimal quantity;
    private BigDecimal filledQuantity;
    private BigDecimal remainingQuantity;
    private BigDecimal avgFillPrice;
    private BigDecimal fee;
    private String rejectedReason;
    private Integer version;
    private Integer expectedVersion;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant submittedAt;
    private Instant filledAt;
}
