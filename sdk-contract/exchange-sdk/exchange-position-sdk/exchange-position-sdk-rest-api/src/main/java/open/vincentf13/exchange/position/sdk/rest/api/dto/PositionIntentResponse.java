package open.vincentf13.exchange.position.sdk.rest.api.dto;

import java.math.BigDecimal;

public record PositionIntentResponse(
        PositionIntentType intentType,
        BigDecimal existingQuantity,
        boolean requiresPositionReservation
) {
    public static PositionIntentResponse of(PositionIntentType type, BigDecimal quantity) {
        return new PositionIntentResponse(type, quantity, type != null && type.requiresPositionReservation());
    }
}
