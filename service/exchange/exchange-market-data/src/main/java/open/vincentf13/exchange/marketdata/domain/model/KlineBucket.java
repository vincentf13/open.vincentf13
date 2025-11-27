package open.vincentf13.exchange.marketdata.domain.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain representation of a single K-line bucket aggregated within a fixed period.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KlineBucket {

    private Long bucketId;
    @NotNull
    private Long instrumentId;
    /**
     * Period identifier such as 1m/5m/1h/1d.
     */
    @NotBlank
    private String period;
    @NotNull
    private Instant bucketStart;
    @NotNull
    private Instant bucketEnd;

    @NotNull
    @DecimalMin(value = ValidationConstant.Names.NON_NEGATIVE, inclusive = true)
    private BigDecimal openPrice;
    @NotNull
    @DecimalMin(value = ValidationConstant.Names.NON_NEGATIVE, inclusive = true)
    private BigDecimal highPrice;
    @NotNull
    @DecimalMin(value = ValidationConstant.Names.NON_NEGATIVE, inclusive = true)
    private BigDecimal lowPrice;
    @NotNull
    @DecimalMin(value = ValidationConstant.Names.NON_NEGATIVE, inclusive = true)
    private BigDecimal closePrice;

    @NotNull
    @DecimalMin(value = ValidationConstant.Names.NON_NEGATIVE, inclusive = true)
    private BigDecimal volume;
    @NotNull
    @DecimalMin(value = ValidationConstant.Names.NON_NEGATIVE, inclusive = true)
    private BigDecimal turnover;
    @NotNull
    @Min(0)
    private Integer tradeCount;
    @NotNull
    @DecimalMin(value = ValidationConstant.Names.NON_NEGATIVE, inclusive = true)
    private BigDecimal takerBuyVolume;
    @NotNull
    @DecimalMin(value = ValidationConstant.Names.NON_NEGATIVE, inclusive = true)
    private BigDecimal takerBuyTurnover;

    private Boolean closed;
    private Instant createdAt;
    private Instant updatedAt;

    public static KlineBucket createEmpty(Long instrumentId,
                                          String period,
                                          Instant bucketStart,
                                          Instant bucketEnd) {
        return KlineBucket.builder()
                .instrumentId(instrumentId)
                .period(period)
                .bucketStart(bucketStart)
                .bucketEnd(bucketEnd)
                .openPrice(BigDecimal.ZERO)
                .highPrice(BigDecimal.ZERO)
                .lowPrice(BigDecimal.ZERO)
                .closePrice(BigDecimal.ZERO)
                .volume(BigDecimal.ZERO)
                .turnover(BigDecimal.ZERO)
                .tradeCount(0)
                .takerBuyVolume(BigDecimal.ZERO)
                .takerBuyTurnover(BigDecimal.ZERO)
                .closed(Boolean.FALSE)
                .build();
    }
}
