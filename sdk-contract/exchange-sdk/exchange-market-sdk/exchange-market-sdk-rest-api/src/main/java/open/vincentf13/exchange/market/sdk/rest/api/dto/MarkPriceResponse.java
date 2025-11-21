package open.vincentf13.exchange.market.sdk.rest.api.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

@Value
@Builder
public class MarkPriceResponse {
    Long instrumentId;
    BigDecimal markPrice;
    Instant calculatedAt;
}
