package open.vincentf13.exchange.risk.margin.sdk.rest.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record LeveragePrecheckRequest(
        @NotNull Long positionId,
        @NotNull Long instrumentId,
        @NotNull Long userId,
        @NotNull @Min(1) Integer targetLeverage,
        BigDecimal quantity,
        BigDecimal margin,
        BigDecimal markPrice
) {}
