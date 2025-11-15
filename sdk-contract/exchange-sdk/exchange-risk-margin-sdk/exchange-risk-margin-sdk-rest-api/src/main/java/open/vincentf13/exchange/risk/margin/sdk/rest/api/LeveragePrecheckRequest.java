package open.vincentf13.exchange.risk.margin.sdk.rest.api;

import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionSide;

import java.math.BigDecimal;

public record LeveragePrecheckRequest(
        Long positionId,
        Long instrumentId,
        Long userId,
        Integer targetLeverage,
        PositionSide side,
        BigDecimal quantity,
        BigDecimal margin,
        BigDecimal markPrice
) {}
