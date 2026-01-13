package open.vincentf13.exchange.position.sdk.rest.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.PositionSide;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionIntentRequest {
  @NotNull private Long userId;

  @NotNull private Long instrumentId;

  @NotNull private PositionSide side;

  @NotNull
  @DecimalMin(value = ValidationConstant.Names.QUANTITY_MIN, inclusive = true)
  private BigDecimal quantity;

  private String clientOrderId;

  // Compatibility methods for record-style access
  public Long userId() {
    return userId;
  }

  public Long instrumentId() {
    return instrumentId;
  }

  public PositionSide side() {
    return side;
  }

  public BigDecimal quantity() {
    return quantity;
  }

  public String clientOrderId() {
    return clientOrderId;
  }
}
