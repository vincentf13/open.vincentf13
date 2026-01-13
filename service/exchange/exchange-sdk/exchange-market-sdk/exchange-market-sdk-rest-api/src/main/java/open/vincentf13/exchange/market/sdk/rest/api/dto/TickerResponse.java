package open.vincentf13.exchange.market.sdk.rest.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TickerResponse {
  Long instrumentId;
  BigDecimal lastPrice;
  BigDecimal volume24h;
  BigDecimal turnover24h;
  BigDecimal high24h;
  BigDecimal low24h;
  BigDecimal open24h;
  BigDecimal priceChange24h;
  BigDecimal priceChangePct;
  Instant capturedAt;
}
