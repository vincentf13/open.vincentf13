package open.vincentf13.exchange.admin.contract.dto;

import open.vincentf13.exchange.admin.contract.enums.InstrumentStatus;
import open.vincentf13.exchange.admin.contract.enums.InstrumentType;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;

import java.math.BigDecimal;
import java.time.Instant;

public record InstrumentDetailResponse(
        Long instrumentId,
        String symbol,
        AssetSymbol baseAsset,
        AssetSymbol quoteAsset,
        InstrumentType instrumentType,
        InstrumentStatus status,
        BigDecimal makerFeeRate,
        BigDecimal takerFeeRate,
        BigDecimal contractSize,
        Instant launchAt,
        Instant delistAt,
        Integer displayOrder,
        Boolean tradable,
        Boolean visible,
        String description,
        String metadata,
        Instant createdAt,
        Instant updatedAt
) {
}
