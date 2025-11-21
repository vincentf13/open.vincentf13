package open.vincentf13.exchange.market.sdk.rest.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;

@Value
@Builder
public class KlineResponse {
    Long instrumentId;
    String period;
    Instant bucketStart;
    Instant bucketEnd;
    @JsonProperty("open")
    BigDecimal openPrice;
    @JsonProperty("high")
    BigDecimal highPrice;
    @JsonProperty("low")
    BigDecimal lowPrice;
    @JsonProperty("close")
    BigDecimal closePrice;
    BigDecimal volume;
    BigDecimal turnover;
}
