package open.vincentf13.exchange.matching.domain.instrument;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;

@Getter
@Builder
@ToString
public class Instrument {

  private final Long instrumentId;
  private final String symbol;
  private final String baseAsset;
  private final String quoteAsset;
  private final BigDecimal makerFee;
  private final BigDecimal takerFee;

  public static Instrument from(InstrumentSummaryResponse dto) {
    return Instrument.builder()
        .instrumentId(dto.instrumentId())
        .symbol(dto.symbol())
        .baseAsset(dto.baseAsset() != null ? dto.baseAsset().name() : null)
        .quoteAsset(dto.quoteAsset() != null ? dto.quoteAsset().name() : null)
        .makerFee(BigDecimal.ZERO)
        .takerFee(dto.takerFeeRate())
        .build();
  }
}
