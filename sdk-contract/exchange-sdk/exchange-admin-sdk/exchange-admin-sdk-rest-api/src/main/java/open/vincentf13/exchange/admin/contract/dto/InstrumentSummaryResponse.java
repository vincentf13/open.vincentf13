package open.vincentf13.exchange.admin.contract.dto;

import open.vincentf13.exchange.admin.contract.enums.InstrumentStatus;
import open.vincentf13.exchange.admin.contract.enums.InstrumentType;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;

import java.math.BigDecimal;

public record InstrumentSummaryResponse(
        Long instrumentId,
        String symbol,
        AssetSymbol baseAsset,
        AssetSymbol quoteAsset,
        InstrumentType instrumentType,
        InstrumentStatus status,
        BigDecimal contractSize,
        Integer displayOrder,
        Boolean tradable,
        Boolean visible,
        BigDecimal takerFeeRate,
        BigDecimal makerFeeRate
) {
}
