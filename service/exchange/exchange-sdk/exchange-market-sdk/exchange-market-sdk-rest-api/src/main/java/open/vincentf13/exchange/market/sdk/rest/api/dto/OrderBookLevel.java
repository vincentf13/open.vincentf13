package open.vincentf13.exchange.market.sdk.rest.api.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class OrderBookLevel {
    BigDecimal price;
    BigDecimal quantity;
}
