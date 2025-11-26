package open.vincentf13.exchange.position.sdk.rest.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.common.sdk.enums.PositionSide;

import java.math.BigDecimal;

public record PositionIntentRequest(
        @NotNull Long userId,
        @NotNull Long instrumentId,
        @NotNull PositionSide side,
        @NotNull @DecimalMin(value = "0.00000001", inclusive = true) BigDecimal quantity
) { }
