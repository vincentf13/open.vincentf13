package open.vincentf13.exchange.order.sdk.rest.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.OrderType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreateRequest {

  public static final int CLIENT_ORDER_ID_MAX_LENGTH = 64;

  @NotNull(message = "instrumentId is required")
  private Long instrumentId;

  @NotNull(message = "side is required")
  private OrderSide side;

  @NotNull(message = "type is required")
  private OrderType type;

  @NotNull(message = "quantity is required")
  @DecimalMin(
      value = ValidationConstant.Names.QUANTITY_MIN,
      inclusive = true,
      message = "quantity must be positive")
  private BigDecimal quantity;

  @DecimalMin(
      value = ValidationConstant.Names.PRICE_MIN,
      inclusive = true,
      message = "price must be positive")
  private BigDecimal price;

  @Size(max = CLIENT_ORDER_ID_MAX_LENGTH, message = "clientOrderId length must be <= 64")
  private String clientOrderId;

  // Compatibility methods for record-style access
  public Long instrumentId() {
    return instrumentId;
  }

  public OrderSide side() {
    return side;
  }

  public OrderType type() {
    return type;
  }

  public BigDecimal quantity() {
    return quantity;
  }

  public BigDecimal price() {
    return price;
  }

  public String clientOrderId() {
    return clientOrderId;
  }
}
