package open.vincentf13.exchange.matching.sdk.mq.event;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;
import open.vincentf13.exchange.matching.sdk.mq.enums.TradeType;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeExecutedEvent(
        @NotNull Long tradeId,
        @NotNull Long instrumentId,
        @NotNull AssetSymbol quoteAsset,
        @NotNull Long makerUserId,
        @NotNull Long takerUserId,
        @NotNull Long orderId,
        @NotNull Long counterpartyOrderId,
        @NotNull OrderSide orderSide,
        @NotNull OrderSide counterpartyOrderSide,
        @NotNull PositionIntentType makerIntent,
        @NotNull PositionIntentType takerIntent,
        TradeType tradeType,
        @NotNull @DecimalMin(value = ValidationConstant.Names.PRICE_MIN) BigDecimal price,
        @NotNull @DecimalMin(value = ValidationConstant.Names.QUANTITY_MIN) BigDecimal quantity,
        BigDecimal totalValue,
        @NotNull @DecimalMin(value = ValidationConstant.Names.FEE_MIN) BigDecimal makerFee,
        @NotNull @DecimalMin(value = ValidationConstant.Names.FEE_MIN) BigDecimal takerFee,
        @NotNull Instant executedAt
) {
}
