package open.vincentf13.exchange.market.sdk.rest.api.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class OrderBookLevel {
  BigDecimal price;
  BigDecimal quantity;
}
