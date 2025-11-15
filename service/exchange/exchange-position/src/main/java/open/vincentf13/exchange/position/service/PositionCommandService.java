package open.vincentf13.exchange.position.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderSide;
import open.vincentf13.exchange.position.domain.model.Position;
import open.vincentf13.exchange.position.domain.model.PositionErrorCode;
import open.vincentf13.exchange.position.infra.persistence.repository.PositionRepository;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionLeverageRequest;
import open.vincentf13.exchange.position.sdk.rest.api.dto.PositionLeverageResponse;
import open.vincentf13.exchange.risk.margin.sdk.rest.api.LeveragePrecheckRequest;
import open.vincentf13.exchange.risk.margin.sdk.rest.api.LeveragePrecheckResponse;
import open.vincentf13.exchange.risk.margin.sdk.rest.client.ExchangeRiskMarginClient;
import open.vincentf13.sdk.core.exception.OpenServiceException;
import open.vincentf13.sdk.spring.mvc.util.OpenApiClientInvoker;
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

    public PositionReserveResult reserveForClose(
            Long userId,
            Long instrumentId,
            BigDecimal quantity,
            OrderSide orderSide
    ) {
        if (userId == null || instrumentId == null) {
            return PositionReserveResult.rejected("POSITION_NOT_FOUND");
        }
        if (quantity == null || quantity.signum() <= 0) {
            return PositionReserveResult.rejected("INVALID_QUANTITY");
        }
        if (orderSide == null) {
            return PositionReserveResult.rejected("ORDER_SIDE_REQUIRED");
        }
        boolean success = positionRepository.reserveForClose(userId, instrumentId, quantity, orderSide);
        if (!success) {
            return PositionReserveResult.rejected("RESERVE_FAILED");
        }
        return PositionReserveResult.accepted(quantity);
    }

    public PositionLeverageResponse adjustLeverage(Long instrumentId, PositionLeverageRequest request) {
        validateLeverageRequest(instrumentId, request);
        Position position = positionRepository.findActive(request.userId(), instrumentId)
                .orElseThrow(() -> OpenServiceException.of(PositionErrorCode.POSITION_NOT_FOUND,
                        "Active position not found"));

        Integer targetLeverage = request.targetLeverage();
        if (targetLeverage.equals(position.getLeverage())) {
            log.info("Leverage unchanged for userId={} instrumentId={}", request.userId(), instrumentId);
            return new PositionLeverageResponse(position.getLeverage(), Instant.now());
        }

        LeveragePrecheckRequest precheckRequest = buildPrecheckRequest(position, targetLeverage);
        LeveragePrecheckResponse precheckResponse = OpenApiClientInvoker.unwrap(
                () -> riskMarginClient.precheckLeverage(precheckRequest),
                "risk.precheck.leverage");
        if (!precheckResponse.allow()) {
            throw OpenServiceException.of(PositionErrorCode.LEVERAGE_PRECHECK_FAILED,
                    "Leverage pre-check rejected", buildPrecheckMeta(position.getPositionId(), instrumentId, targetLeverage, precheckResponse));
        }

        boolean updated = positionRepository.updateLeverage(position.getPositionId(), targetLeverage);
        if (!updated) {
            throw OpenServiceException.of(PositionErrorCode.POSITION_NOT_FOUND,
                    "Failed to update leverage due to concurrent modification");
        }
        log.info("Position leverage updated. positionId={} userId={} instrumentId={} leverage={} -> {}",
                position.getPositionId(), position.getUserId(), instrumentId, position.getLeverage(), targetLeverage);
        return new PositionLeverageResponse(targetLeverage, Instant.now());
    }

    private void validateLeverageRequest(Long instrumentId, PositionLeverageRequest request) {
        if (instrumentId == null) {
            throw OpenServiceException.of(PositionErrorCode.POSITION_NOT_FOUND, "instrumentId is required");
        }
        if (request == null || request.targetLeverage() == null) {
            throw OpenServiceException.of(PositionErrorCode.INVALID_LEVERAGE_REQUEST, "targetLeverage is required");
        }
        if (request.userId() == null) {
            throw OpenServiceException.of(PositionErrorCode.INVALID_LEVERAGE_REQUEST, "userId is required");
        }
        if (request.targetLeverage() <= 0) {
            throw OpenServiceException.of(PositionErrorCode.INVALID_LEVERAGE_REQUEST, "targetLeverage must be positive");
        }
    }

    private LeveragePrecheckRequest buildPrecheckRequest(Position position, Integer targetLeverage) {
        BigDecimal margin = position.getMargin();
        BigDecimal equity = resolveEquity(position);
        return new LeveragePrecheckRequest(
                position.getPositionId(),
                position.getInstrumentId(),
                position.getUserId(),
                targetLeverage,
                position.getSide(),
                position.getQuantity(),
                equity,
                margin,
                position.getMarkPrice()
        );
    }

    private BigDecimal resolveEquity(Position position) {
        BigDecimal margin = defaultZero(position.getMargin());
        BigDecimal realized = defaultZero(position.getRealizedPnl());
        BigDecimal unrealized = defaultZero(position.getUnrealizedPnl());
        return margin.add(realized).add(unrealized);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
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
}
