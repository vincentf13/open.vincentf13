package open.vincentf13.exchange.position.sdk.rest.api.dto;

import java.time.Instant;

public record PositionLeverageResponse(
        Integer appliedLeverage,
        Instant effectiveAt
) {
}
