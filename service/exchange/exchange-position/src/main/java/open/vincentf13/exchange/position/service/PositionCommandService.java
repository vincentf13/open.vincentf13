package open.vincentf13.exchange.position.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.PositionSide;
import open.vincentf13.exchange.common.sdk.enums.PositionStatus;
import open.vincentf13.exchange.position.domain.model.Position;
import open.vincentf13.exchange.position.infra.PositionErrorCode;
import open.vincentf13.exchange.position.infra.PositionEvent;
import open.vincentf13.exchange.position.infra.persistence.po.PositionPO;
import open.vincentf13.exchange.position.infra.persistence.repository.PositionRepository;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionLeverageRequest;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionLeverageResponse;
import open.vincentf13.exchange.risk.margin.sdk.rest.api.LeveragePrecheckRequest;
import open.vincentf13.exchange.risk.margin.sdk.rest.api.LeveragePrecheckResponse;
import open.vincentf13.exchange.risk.margin.sdk.rest.client.ExchangeRiskMarginClient;
import open.vincentf13.sdk.core.exception.OpenException;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.spring.cloud.openfeign.OpenApiClientInvoker;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Validated
public class PositionCommandService {
    
    private final PositionRepository positionRepository;
    private final ExchangeRiskMarginClient riskMarginClient;
    
    public PositionReserveOutcome reserveForClose(
            @NotNull Long orderId,
            @NotNull Long userId,
            @NotNull Long instrumentId,
            @NotNull @DecimalMin(value = ValidationConstant.Names.QUANTITY_MIN, inclusive = true) BigDecimal quantity,
            @NotNull PositionSide side
                                                 ) {
        Position position = positionRepository.findOne(
                                                      Wrappers.lambdaQuery(PositionPO.class)
                                                              .eq(PositionPO::getUserId, userId)
                                                              .eq(PositionPO::getInstrumentId, instrumentId)
                                                              .eq(PositionPO::getStatus, PositionStatus.ACTIVE))
                                              .orElse(null);
        if (position == null) {
            return PositionReserveOutcome.rejected("POSITION_NOT_FOUND");
        }
        if (position.availableToClose().compareTo(quantity) < 0) {
            return PositionReserveOutcome.rejected("INSUFFICIENT_AVAILABLE");
        }
        int expectedVersion = position.safeVersion();
        Position updateRecord = Position.builder()
                                        .closingReservedQuantity(position.getClosingReservedQuantity().add(quantity))
                                        .version(expectedVersion + 1)
                                        .build();
        boolean success = positionRepository.updateSelectiveBy(
                updateRecord,
                new LambdaUpdateWrapper<PositionPO>()
                        .eq(PositionPO::getPositionId, position.getPositionId())
                        .eq(PositionPO::getUserId, position.getUserId())
                        .eq(PositionPO::getInstrumentId, position.getInstrumentId())
                        .eq(PositionPO::getSide, side)
                        .eq(PositionPO::getStatus, PositionStatus.ACTIVE)
                        .eq(PositionPO::getVersion, expectedVersion)
                                                              );
        if (!success) {
            return PositionReserveOutcome.rejected("RESERVE_FAILED");
        }
        BigDecimal avgOpenPrice = position.getEntryPrice();
        return PositionReserveOutcome.accepted(quantity, avgOpenPrice);
    }
    
    public PositionLeverageResponse adjustLeverage(@NotNull Long userId,
                                                   @NotNull Long instrumentId,
                                                   @Valid PositionLeverageRequest request) {
        Position position = positionRepository.findOne(
                                                      Wrappers.lambdaQuery(PositionPO.class)
                                                              .eq(PositionPO::getUserId, userId)
                                                              .eq(PositionPO::getInstrumentId, instrumentId)
                                                              .eq(PositionPO::getStatus, PositionStatus.ACTIVE))
                                              .orElseGet(() -> positionRepository.createDefault(userId, instrumentId));
        if (position == null) {
            throw OpenException.of(PositionErrorCode.POSITION_NOT_FOUND,
                                   Map.of("instrumentId", instrumentId, "userId", userId));
        }
        
        Integer targetLeverage = request.targetLeverage();
        if (targetLeverage.equals(position.getLeverage())) {
            OpenLog.info(PositionEvent.POSITION_LEVERAGE_UNCHANGED, "userId", userId, "instrumentId", instrumentId);
            return new PositionLeverageResponse(position.getLeverage(), Instant.now());
        }
        
        LeveragePrecheckRequest precheckRequest = buildPrecheckRequest(position, targetLeverage);
        LeveragePrecheckResponse precheckResponse = OpenApiClientInvoker.call(
                () -> riskMarginClient.precheckLeverage(precheckRequest),
                msg -> OpenException.of(PositionErrorCode.LEVERAGE_PRECHECK_FAILED,
                                        Map.of("positionId", position.getPositionId(), "remoteMessage", msg))
                                                                             );
        if (!precheckResponse.allow()) {
            throw OpenException.of(PositionErrorCode.LEVERAGE_PRECHECK_FAILED,
                                   buildPrecheckMeta(position.getPositionId(), instrumentId, targetLeverage, precheckResponse));
        }
        
        int expectedVersion = position.safeVersion();
        Position updateRecord = Position.builder()
                                        .leverage(targetLeverage)
                                        .version(expectedVersion + 1)
                                        .build();
        boolean updated = positionRepository.updateSelectiveBy(
                updateRecord,
                new LambdaUpdateWrapper<PositionPO>()
                        .eq(PositionPO::getPositionId, position.getPositionId())
                        .eq(PositionPO::getUserId, position.getUserId())
                        .eq(PositionPO::getInstrumentId, position.getInstrumentId())
                        .eq(PositionPO::getSide, position.getSide())
                        .eq(PositionPO::getStatus, PositionStatus.ACTIVE)
                        .eq(PositionPO::getVersion, expectedVersion)
                                                              );
        if (!updated) {
            throw OpenException.of(PositionErrorCode.POSITION_NOT_FOUND,
                                   Map.of("positionId", position.getPositionId(), "instrumentId", instrumentId));
        }
        OpenLog.info(PositionEvent.POSITION_LEVERAGE_UPDATED, "positionId", position.getPositionId(), "userId", position.getUserId(), "instrumentId", instrumentId, "fromLeverage", position.getLeverage(), "toLeverage", targetLeverage);
        return new PositionLeverageResponse(targetLeverage, Instant.now());
    }
    
    private LeveragePrecheckRequest buildPrecheckRequest(Position position,
                                                         Integer targetLeverage) {
        return new LeveragePrecheckRequest(
                position.getPositionId(),
                position.getInstrumentId(),
                position.getUserId(),
                targetLeverage,
                position.getQuantity(),
                position.getMargin(),
                position.getMarkPrice()
        );
    }
    
    private Map<String, Object> buildPrecheckMeta(Long positionId,
                                                  Long instrumentId,
                                                  Integer targetLeverage,
                                                  LeveragePrecheckResponse response) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("positionId", positionId);
        meta.put("instrumentId", instrumentId);
        meta.put("targetLeverage", targetLeverage);
        if (response != null) {
            meta.put("suggestedLeverage", response.suggestedLeverage());
            meta.put("deficit", response.deficit());
            meta.put("reason", response.reason());
        }
        return meta;
    }
    
    public record PositionReserveResult(boolean success, BigDecimal reservedQuantity, String reason,
                                        Instant processedAt) {
        public static PositionReserveResult accepted(BigDecimal quantity) {
            return new PositionReserveResult(true, quantity, null, Instant.now());
        }
        
        public static PositionReserveResult rejected(String reason) {
            return new PositionReserveResult(false, BigDecimal.ZERO, reason, Instant.now());
        }
        
        public boolean isCloseIntent() {
            return true;
        }
    }
    
    public record PositionReserveOutcome(PositionReserveResult result, BigDecimal avgOpenPrice) {
        public static PositionReserveOutcome accepted(BigDecimal reservedQuantity,
                                                      BigDecimal avgOpenPrice) {
            return new PositionReserveOutcome(PositionReserveResult.accepted(reservedQuantity), avgOpenPrice);
        }
        
        public static PositionReserveOutcome rejected(String reason) {
            return new PositionReserveOutcome(PositionReserveResult.rejected(reason), null);
        }
    }
}
