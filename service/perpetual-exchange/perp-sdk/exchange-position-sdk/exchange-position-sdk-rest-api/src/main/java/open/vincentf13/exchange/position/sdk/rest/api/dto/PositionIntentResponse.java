package open.vincentf13.exchange.position.sdk.rest.api.dto;

import java.math.BigDecimal;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;

public record PositionIntentResponse(
    PositionIntentType intentType,
    BigDecimal existingQuantity,
    String rejectReason,
    PositionResponse positionSnapshot) {
  public static PositionIntentResponse of(PositionIntentType type, BigDecimal quantity) {
    return new PositionIntentResponse(type, quantity, null, null);
  }

  public static PositionIntentResponse of(
      PositionIntentType type, BigDecimal quantity, PositionResponse snapshot) {
    return new PositionIntentResponse(type, quantity, null, snapshot);
  }

  public static PositionIntentResponse ofRejected(
      PositionIntentType type, BigDecimal quantity, String reason) {
    return new PositionIntentResponse(type, quantity, reason, null);
  }
}
