package open.vincentf13.exchange.risk.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkPriceSnapshot {

    private Long instrumentId;
    private BigDecimal markPrice;
    private BigDecimal indexPrice;
    private BigDecimal fairPrice;
    private Instant calculatedAt;
}
