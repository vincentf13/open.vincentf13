package open.vincentf13.exchange.matching.sdk.rest.dto;

import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;
import open.vincentf13.exchange.common.sdk.enums.TradeType;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeResponse(
        Long tradeId,
        Long instrumentId,
        Long makerUserId,
        Long takerUserId,
        Long orderId,
        Long counterpartyOrderId,
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
        Instant createdAt
) {
}
