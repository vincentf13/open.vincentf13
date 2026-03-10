package open.vincentf13.exchange.matching.sdk.rest.dto;

import java.math.BigDecimal;
import java.time.Instant;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;
import open.vincentf13.exchange.common.sdk.enums.TradeType;

public record TradeResponse(
    Long tradeId,
    Long instrumentId,
    Long makerUserId,
    Long takerUserId,
    Long orderId,
    Long counterpartyOrderId,
    BigDecimal orderQuantity,
    // cumulative filled quantity including this trade
    BigDecimal orderFilledQuantity,
    BigDecimal counterpartyOrderQuantity,
    // cumulative filled quantity including this trade
    BigDecimal counterpartyOrderFilledQuantity,
    OrderSide orderSide,
    OrderSide counterpartyOrderSide,
    PositionIntentType makerIntent,
    PositionIntentType takerIntent,
    TradeType tradeType,
    BigDecimal price,
    BigDecimal quantity,
    BigDecimal totalValue,
    BigDecimal makerFee,
    BigDecimal takerFee,
    Instant executedAt,
    Instant createdAt) {}
