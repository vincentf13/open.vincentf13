package open.vincentf13.exchange.position.sdk.rest.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PositionReservationReleaseRequest {
  @NotNull private Long userId;

  @NotNull private Long instrumentId;

  @NotNull
  @DecimalMin(value = ValidationConstant.Names.QUANTITY_MIN, inclusive = true)
  private BigDecimal quantity;

  @NotNull private String clientOrderId;

  public Long userId() {
    return userId;
  }

  public Long instrumentId() {
    return instrumentId;
  }

  public BigDecimal quantity() {
    return quantity;
  }

  public String clientOrderId() {
    return clientOrderId;
  }
}
