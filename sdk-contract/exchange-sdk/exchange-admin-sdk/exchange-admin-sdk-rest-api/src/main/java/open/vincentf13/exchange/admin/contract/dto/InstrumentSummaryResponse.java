package open.vincentf13.exchange.admin.contract.dto;

import open.vincentf13.exchange.admin.contract.enums.InstrumentStatus;
import open.vincentf13.exchange.admin.contract.enums.InstrumentType;

import java.math.BigDecimal;

public record InstrumentSummaryResponse(
        Long instrumentId,
        String symbol,
        String baseAsset,
        String quoteAsset,
        InstrumentType instrumentType,
        InstrumentStatus status,
        BigDecimal tickSize,
        BigDecimal lotSize,
        BigDecimal contractSize,
        Integer displayOrder,
        Boolean tradable,
        Boolean visible
) {
}
