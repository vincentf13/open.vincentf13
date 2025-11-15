package open.vincentf13.exchange.position.sdk.rest.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PositionLeverageRequest(
        @NotNull Long userId,
        @NotNull @Min(1) Integer targetLeverage
) { }
