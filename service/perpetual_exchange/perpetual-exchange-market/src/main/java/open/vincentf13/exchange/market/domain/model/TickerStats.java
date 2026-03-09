package open.vincentf13.exchange.market.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TickerStats {

  private Long instrumentId;
  private BigDecimal lastPrice;
  private BigDecimal volume24h;
  private BigDecimal turnover24h;
  private BigDecimal high24h;
  private BigDecimal low24h;
  private BigDecimal open24h;
  private BigDecimal priceChange24h;
  private BigDecimal priceChangePct;
  private Instant updatedAt;
}
