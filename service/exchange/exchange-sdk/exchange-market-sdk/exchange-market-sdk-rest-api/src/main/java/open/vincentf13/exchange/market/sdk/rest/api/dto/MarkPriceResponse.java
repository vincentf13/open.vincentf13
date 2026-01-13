package open.vincentf13.exchange.market.sdk.rest.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MarkPriceResponse {
  Long instrumentId;
  BigDecimal markPrice;
  BigDecimal markPriceChangeRate;
  Instant calculatedAt;
}
