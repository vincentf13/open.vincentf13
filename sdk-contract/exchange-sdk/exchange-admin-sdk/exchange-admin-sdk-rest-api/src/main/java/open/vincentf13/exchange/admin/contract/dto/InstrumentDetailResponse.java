package open.vincentf13.exchange.admin.contract.dto;

import open.vincentf13.exchange.admin.contract.enums.InstrumentStatus;
import open.vincentf13.exchange.admin.contract.enums.InstrumentType;

import java.math.BigDecimal;
import java.time.Instant;

public record InstrumentDetailResponse(
        Long instrumentId,
        String symbol,
        String baseAsset,
        String quoteAsset,
        InstrumentType instrumentType,
        InstrumentStatus status,
        BigDecimal tickSize,
        BigDecimal lotSize,
        BigDecimal minOrderValue,
        BigDecimal maxOrderValue,
        BigDecimal minNotional,
        Integer pricePrecision,
        Integer quantityPrecision,
        BigDecimal makerFeeRate,
        BigDecimal takerFeeRate,
        BigDecimal contractSize,
        String settlementAsset,
        Integer maxLeverage,
        BigDecimal maintenanceMargin,
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
