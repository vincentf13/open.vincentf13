package open.vincentf13.exchange.matching.domain.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;
import open.vincentf13.exchange.matching.sdk.mq.enums.TradeType;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trade {
    
    private Long tradeId;
    private Long instrumentId;
    private AssetSymbol quoteAsset;
    private Long makerUserId;
    private Long takerUserId;
    private Long orderId;
    private Long counterpartyOrderId;
    private OrderSide orderSide;
    private OrderSide counterpartyOrderSide;
    private PositionIntentType makerIntent;
    private PositionIntentType takerIntent;
    private TradeType tradeType;
    @DecimalMin(value = ValidationConstant.Names.PRICE_MIN, inclusive = true)
    private BigDecimal price;
    @DecimalMin(value = ValidationConstant.Names.QUANTITY_MIN, inclusive = true)
    private BigDecimal quantity;
    private BigDecimal totalValue;
    @DecimalMin(value = ValidationConstant.Names.FEE_MIN, inclusive = true)
    private BigDecimal makerFee;
    @DecimalMin(value = ValidationConstant.Names.FEE_MIN, inclusive = true)
    private BigDecimal takerFee;
    @NotNull
    private Instant executedAt;
    private Instant createdAt;
    

}
