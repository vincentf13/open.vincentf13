package open.vincentf13.exchange.position.sdk.rest.api.dto;

import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionSide;

import java.math.BigDecimal;

public record PositionIntentRequest(
        @NotNull Long userId,
        @NotNull Long instrumentId,
        @NotNull PositionSide side,
        @NotNull BigDecimal quantity
) { }
