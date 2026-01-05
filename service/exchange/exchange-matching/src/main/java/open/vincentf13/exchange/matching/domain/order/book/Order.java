package open.vincentf13.exchange.matching.domain.order.book;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.OrderType;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;
import open.vincentf13.exchange.common.sdk.enums.TradeType;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    
    @NotNull
    private Long orderId;
    @NotNull
    private Long userId;
    @NotNull
    private Long instrumentId;
    @NotNull
    private OrderSide side;
    @NotNull
    private OrderType type;
    private PositionIntentType intent;
    private TradeType tradeType;
    @DecimalMin(value = ValidationConstant.Names.PRICE_MIN, inclusive = true)
    private BigDecimal price;
    @DecimalMin(value = ValidationConstant.Names.QUANTITY_MIN, inclusive = true)
    private BigDecimal quantity;
    private BigDecimal originalQuantity;
    private String clientOrderId;
    @NotNull
    private Instant submittedAt;
    
    public boolean isBuy() {
        return side == OrderSide.BUY;
    }
}
