package open.vincentf13.exchange.position.domain.model;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;
import open.vincentf13.exchange.common.sdk.enums.PositionSide;
import open.vincentf13.exchange.common.sdk.enums.PositionStatus;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Position {
    
    private Long positionId;
    @NotNull
    private Long userId;
    @NotNull
    private Long instrumentId;
    @NotNull
    private Integer leverage;
    @NotNull
    private BigDecimal margin;
    @NotNull
    private PositionSide side;
    @NotNull
    private BigDecimal entryPrice;
    @NotNull
    private BigDecimal quantity;
    @NotNull
    private BigDecimal closingReservedQuantity;
    @NotNull
    private BigDecimal markPrice;
    @NotNull
    private BigDecimal marginRatio;
    @NotNull
    private BigDecimal unrealizedPnl;
    @NotNull
    private BigDecimal liquidationPrice;
    @NotNull
    private BigDecimal cumRealizedPnl;
    @NotNull
    private BigDecimal cumFee;
    @NotNull
    private BigDecimal cumFundingFee;
    
    @NotNull
    private PositionStatus status;
    private Integer version;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant closedAt;
    
    public static Position createDefault(Long userId,
                                         Long instrumentId,
                                         PositionSide side) {
        return Position.builder()
                       .userId(userId)
                       .instrumentId(instrumentId)
                       .leverage(40)
                       .margin(BigDecimal.ZERO)
                       .side(side == null ? PositionSide.LONG : side)
                       .entryPrice(BigDecimal.ZERO)
                       .quantity(BigDecimal.ZERO)
                       .closingReservedQuantity(BigDecimal.ZERO)
                       .markPrice(BigDecimal.ZERO)
                       .marginRatio(BigDecimal.ZERO)
                       .unrealizedPnl(BigDecimal.ZERO)
                       .liquidationPrice(BigDecimal.ZERO)
                       .cumRealizedPnl(BigDecimal.ZERO)
                       .cumFee(BigDecimal.ZERO)
                       .cumFundingFee(BigDecimal.ZERO)
                       .version(0)
                       .status(PositionStatus.ACTIVE)
                       .build();
    }
    
    public int safeVersion() {
        return version == null ? 0 : version;
    }
    
    public BigDecimal availableToClose() {
        BigDecimal reserved = closingReservedQuantity == null ? BigDecimal.ZERO : closingReservedQuantity;
        BigDecimal currentQuantity = quantity == null ? BigDecimal.ZERO : quantity;
        BigDecimal available = currentQuantity.subtract(reserved);
        return available.max(BigDecimal.ZERO);
    }
    
    
    public boolean isSameSide(PositionSide otherSide) {
        if (side == null || otherSide == null) {
            return true;
        }
        return side == otherSide;
    }
    
    public PositionIntentType evaluateIntent(PositionSide requestSide,
                                             BigDecimal requestedQuantity) {
        BigDecimal requested = requestedQuantity == null ? BigDecimal.ZERO : requestedQuantity;
        if (isSameSide(requestSide)) {
            return PositionIntentType.INCREASE;
        }
        BigDecimal current = quantity == null ? BigDecimal.ZERO : quantity;
        return current.compareTo(requested) > 0 ? PositionIntentType.REDUCE : PositionIntentType.CLOSE;
    }

    public static String buildPayload(Position before,
                                      Position after) {
        Position baseline = (before == null || before.getPositionId() == null) ? new Position() : before;
        Map<String, Object> payload = new LinkedHashMap<>();
        appendPayload(payload, "positionId", baseline.getPositionId(), after.getPositionId());
        appendPayload(payload, "userId", baseline.getUserId(), after.getUserId());
        appendPayload(payload, "instrumentId", baseline.getInstrumentId(), after.getInstrumentId());
        appendPayload(payload, "side",
                baseline.getSide() != null ? baseline.getSide().name() : null,
                after.getSide() != null ? after.getSide().name() : null);
        appendPayload(payload, "leverage", baseline.getLeverage(), after.getLeverage());
        appendPayload(payload, "status",
                baseline.getStatus() != null ? baseline.getStatus().name() : null,
                after.getStatus() != null ? after.getStatus().name() : null);
        appendPayload(payload, "quantity", baseline.getQuantity(), after.getQuantity());
        appendPayload(payload, "entryPrice", baseline.getEntryPrice(), after.getEntryPrice());
        appendPayload(payload, "margin", baseline.getMargin(), after.getMargin());
        appendPayload(payload, "closingReservedQuantity", baseline.getClosingReservedQuantity(), after.getClosingReservedQuantity());
        appendPayload(payload, "markPrice", baseline.getMarkPrice(), after.getMarkPrice());
        appendPayload(payload, "unrealizedPnl", baseline.getUnrealizedPnl(), after.getUnrealizedPnl());
        appendPayload(payload, "marginRatio", baseline.getMarginRatio(), after.getMarginRatio());
        appendPayload(payload, "liquidationPrice", baseline.getLiquidationPrice(), after.getLiquidationPrice());
        appendPayload(payload, "cumRealizedPnl", baseline.getCumRealizedPnl(), after.getCumRealizedPnl());
        appendPayload(payload, "cumFee", baseline.getCumFee(), after.getCumFee());
        appendPayload(payload, "cumFundingFee", baseline.getCumFundingFee(), after.getCumFundingFee());
        appendPayload(payload, "version", baseline.getVersion(), after.getVersion());
        appendPayload(payload, "createdAt", baseline.getCreatedAt(), after.getCreatedAt());
        appendPayload(payload, "updatedAt", baseline.getUpdatedAt(), after.getUpdatedAt());
        appendPayload(payload, "closedAt", baseline.getClosedAt(), after.getClosedAt());
        return OpenObjectMapper.toJson(payload);
    }

    private static void appendPayload(Map<String, Object> payload,
                                      String key,
                                      Object before,
                                      Object after) {
        if (!isSameValue(before, after)) {
            payload.put(key, after);
        }
    }

    private static boolean isSameValue(Object before, Object after) {
        if (before == after) {
            return true;
        }
        if (before == null || after == null) {
            return false;
        }
        if (before instanceof BigDecimal && after instanceof BigDecimal) {
            return ((BigDecimal) before).compareTo((BigDecimal) after) == 0;
        }
        return Objects.equals(before, after);
    }
}
