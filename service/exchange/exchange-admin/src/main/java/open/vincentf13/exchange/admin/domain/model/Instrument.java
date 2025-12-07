package open.vincentf13.exchange.admin.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.admin.contract.enums.InstrumentStatus;
import open.vincentf13.exchange.admin.contract.enums.InstrumentType;
import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Instrument {
    
    private Long instrumentId;
    private String symbol;
    private AssetSymbol baseAsset;
    private AssetSymbol quoteAsset;
    private InstrumentType instrumentType;
    private InstrumentStatus status;
    private BigDecimal makerFeeRate;
    private BigDecimal takerFeeRate;
    private BigDecimal contractSize;
    private Instant launchAt;
    private Instant delistAt;
    private Integer displayOrder;
    private Boolean tradable;
    private Boolean visible;
    private String description;
    private String metadata;
    private Instant createdAt;
    private Instant updatedAt;
}
