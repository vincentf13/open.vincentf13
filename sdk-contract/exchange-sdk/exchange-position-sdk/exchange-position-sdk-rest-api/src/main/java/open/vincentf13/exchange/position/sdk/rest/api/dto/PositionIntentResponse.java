package open.vincentf13.exchange.position.sdk.rest.api.dto;

import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;

import java.math.BigDecimal;

public record PositionIntentResponse(
        PositionIntentType intentType,
        BigDecimal existingQuantity,
        String rejectReason
) {
    public static PositionIntentResponse of(PositionIntentType type,
                                            BigDecimal quantity) {
        return new PositionIntentResponse(type, quantity, null);
    }
    
    public static PositionIntentResponse ofRejected(PositionIntentType type,
                                                    BigDecimal quantity,
                                                    String reason) {
        return new PositionIntentResponse(type, quantity, reason);
    }
}
