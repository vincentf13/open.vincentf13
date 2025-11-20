package open.vincentf13.exchange.risk.margin.sdk.rest.api;

import java.math.BigDecimal;

public record LeveragePrecheckRequest(
        Long positionId,
        Long instrumentId,
        Long userId,
        Integer targetLeverage,
        BigDecimal quantity,
        BigDecimal margin,
        BigDecimal markPrice
) {}
