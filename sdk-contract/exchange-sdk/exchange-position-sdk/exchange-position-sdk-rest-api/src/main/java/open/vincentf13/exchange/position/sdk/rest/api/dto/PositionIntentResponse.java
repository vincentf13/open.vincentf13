package open.vincentf13.exchange.position.sdk.rest.api.dto;

import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;

import java.math.BigDecimal;

public record PositionIntentResponse(
        PositionIntentType intentType,
        BigDecimal existingQuantity,
        BigDecimal closingEntryPrice,
        String rejectReason
) {
    public static PositionIntentResponse of(PositionIntentType type,
                                            BigDecimal quantity) {
        return new PositionIntentResponse(type, quantity, null, null);
    }
    
    public static PositionIntentResponse of(PositionIntentType type,
                                            BigDecimal quantity,
                                            BigDecimal closingEntryPrice) {
        return new PositionIntentResponse(type, quantity, closingEntryPrice, null);
    }
    
    public static PositionIntentResponse ofRejected(PositionIntentType type,
                                                    BigDecimal quantity,
                                                    String reason) {
        return new PositionIntentResponse(type, quantity, null, reason);
    }
}
