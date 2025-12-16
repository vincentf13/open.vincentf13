package open.vincentf13.exchange.matching.domain.instrument;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;

@Getter
@Builder
@ToString
public class Instrument {

  private final Long instrumentId;
  private final String symbol;
  private final AssetSymbol baseAsset;
  private final AssetSymbol quoteAsset;
  private final BigDecimal makerFee;
  private final BigDecimal takerFee;

  public static Instrument from(InstrumentSummaryResponse dto) {
    return Instrument.builder()
        .instrumentId(dto.instrumentId())
        .symbol(dto.symbol())
        .baseAsset(dto.baseAsset())
        .quoteAsset(dto.quoteAsset())
        .makerFee(dto.makerFeeRate())
        .takerFee(dto.takerFeeRate())
        .build();
  }
}
