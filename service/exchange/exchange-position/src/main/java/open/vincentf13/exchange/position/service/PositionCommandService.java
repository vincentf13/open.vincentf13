package open.vincentf13.exchange.position.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.position.domain.model.Position;
import open.vincentf13.exchange.position.domain.model.PositionErrorCode;
import open.vincentf13.exchange.position.infra.persistence.repository.PositionRepository;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionLeverageRequest;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionLeverageResponse;
import open.vincentf13.exchange.position.sdk.rest.api.enums.PositionSide;
import open.vincentf13.exchange.risk.margin.sdk.rest.api.LeveragePrecheckRequest;
import open.vincentf13.exchange.risk.margin.sdk.rest.api.LeveragePrecheckResponse;
import open.vincentf13.exchange.risk.margin.sdk.rest.client.ExchangeRiskMarginClient;
import open.vincentf13.sdk.core.OpenValidator;
import open.vincentf13.sdk.core.exception.OpenServiceException;
import open.vincentf13.sdk.spring.cloud.openfeign.OpenApiClientInvoker;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PositionCommandService {

    private final PositionRepository positionRepository;
    private final ExchangeRiskMarginClient riskMarginClient;

    public PositionReserveOutcome reserveForClose(
            Long orderId,
            Long userId,
            Long instrumentId,
            BigDecimal quantity,
            PositionSide side
    ) {
        if (userId == null || instrumentId == null) {
            return PositionReserveOutcome.rejected("POSITION_NOT_FOUND");
        }
        if (quantity == null || quantity.signum() <= 0) {
            return PositionReserveOutcome.rejected("INVALID_QUANTITY");
        }
        if (side == null) {
            return PositionReserveOutcome.rejected("ORDER_SIDE_REQUIRED");
        }
        Position position = positionRepository.findOne(Position.builder()
                        .userId(userId)
                        .instrumentId(instrumentId)
                        .status("ACTIVE")
                        .build())
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
                position.getPositionId(),
                position.getUserId(),
                position.getInstrumentId(),
                side,
                expectedVersion,
                "ACTIVE");
        if (!success) {
            return PositionReserveOutcome.rejected("RESERVE_FAILED");
        }
        BigDecimal avgOpenPrice = position.getEntryPrice();
        return PositionReserveOutcome.accepted(quantity, avgOpenPrice);
    }

    public PositionLeverageResponse adjustLeverage(Long userId, Long instrumentId, PositionLeverageRequest request) {
        if (instrumentId == null) {
            throw OpenServiceException.of(PositionErrorCode.POSITION_NOT_FOUND, "instrumentId is required");
        }
        OpenValidator.validateOrThrow(request);
        Position position = positionRepository.findOne(Position.builder()
                        .userId(userId)
                        .instrumentId(instrumentId)
                        .status("ACTIVE")
                        .build())
                .orElseGet(() -> positionRepository.createDefault(userId, instrumentId));
        if (position == null) {
            throw OpenServiceException.of(PositionErrorCode.POSITION_NOT_FOUND,
                    "Failed to initialize position for leverage adjustment");
        }

        Integer targetLeverage = request.targetLeverage();
        if (targetLeverage.equals(position.getLeverage())) {
            log.info("Leverage unchanged for userId={} instrumentId={}", userId, instrumentId);
            return new PositionLeverageResponse(position.getLeverage(), Instant.now());
        }

        LeveragePrecheckRequest precheckRequest = buildPrecheckRequest(position, targetLeverage);
        LeveragePrecheckResponse precheckResponse = OpenApiClientInvoker.call(
                () -> riskMarginClient.precheckLeverage(precheckRequest),
                msg -> OpenServiceException.of(PositionErrorCode.LEVERAGE_PRECHECK_FAILED,
                        "Failed to invoke leverage pre-check for position %s: %s"
                                .formatted(position.getPositionId(), msg))
        );
        if (!precheckResponse.allow()) {
            throw OpenServiceException.of(PositionErrorCode.LEVERAGE_PRECHECK_FAILED,
                    "Leverage pre-check rejected", buildPrecheckMeta(position.getPositionId(), instrumentId, targetLeverage, precheckResponse));
        }

        int expectedVersion = position.safeVersion();
        Position updateRecord = Position.builder()
                .leverage(targetLeverage)
                .version(expectedVersion + 1)
                .build();
        boolean updated = positionRepository.updateSelectiveBy(
                updateRecord,
                position.getPositionId(),
                position.getUserId(),
                position.getInstrumentId(),
                position.getSide(),
                expectedVersion,
                "ACTIVE");
        if (!updated) {
            throw OpenServiceException.of(PositionErrorCode.POSITION_NOT_FOUND,
                    "Failed to update leverage due to concurrent modification");
        }
        log.info("Position leverage updated. positionId={} userId={} instrumentId={} leverage={} -> {}",
                position.getPositionId(), position.getUserId(), instrumentId, position.getLeverage(), targetLeverage);
        return new PositionLeverageResponse(targetLeverage, Instant.now());
    }

    private LeveragePrecheckRequest buildPrecheckRequest(Position position, Integer targetLeverage) {
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

    public record PositionReserveResult(boolean success, BigDecimal reservedQuantity, String reason, Instant processedAt) {
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
        public static PositionReserveOutcome accepted(BigDecimal reservedQuantity, BigDecimal avgOpenPrice) {
            return new PositionReserveOutcome(PositionReserveResult.accepted(reservedQuantity), avgOpenPrice);
        }

        public static PositionReserveOutcome rejected(String reason) {
            return new PositionReserveOutcome(PositionReserveResult.rejected(reason), null);
        }
    }
}
