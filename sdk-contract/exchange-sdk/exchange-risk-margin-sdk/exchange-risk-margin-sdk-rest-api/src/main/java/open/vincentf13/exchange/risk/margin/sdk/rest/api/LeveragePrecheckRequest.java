package open.vincentf13.exchange.risk.margin.sdk.rest.api;

import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderSide;

import java.math.BigDecimal;

public record LeveragePrecheckRequest(
        Long positionId,
        Long instrumentId,
        Long userId,
        Integer targetLeverage,
        OrderSide side,
        BigDecimal quantity,
        BigDecimal margin,
        BigDecimal markPrice
) {}
