package open.vincentf13.exchange.risk.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.risk.domain.model.RiskLimit;
import open.vincentf13.exchange.risk.domain.model.RiskSnapshot;
import open.vincentf13.exchange.risk.infra.persistence.repository.RiskLimitRepository;
import open.vincentf13.exchange.risk.infra.persistence.repository.RiskSnapshotRepository;
import open.vincentf13.exchange.risk.margin.sdk.rest.api.LeveragePrecheckRequest;
import open.vincentf13.exchange.risk.margin.sdk.rest.api.LeveragePrecheckResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeveragePrecheckService {

    private static final MathContext DIVISION_CONTEXT = new MathContext(18, RoundingMode.HALF_UP);

    private final RiskLimitRepository riskLimitRepository;
    private final RiskSnapshotRepository riskSnapshotRepository;

    public LeveragePrecheckResponse precheck(LeveragePrecheckRequest request) {
        validate(request);
        Instant now = Instant.now();
        RiskLimit riskLimit = riskLimitRepository.findEffective(request.instrumentId(), null, now)
                .orElse(null);
        if (riskLimit == null) {
            log.warn("Leverage pre-check failed: risk limit missing. instrumentId={} positionId={}",
                    request.instrumentId(), request.positionId());
            return new LeveragePrecheckResponse(false, null, null, "RISK_LIMIT_NOT_FOUND");
        }
        Integer maxLeverage = Optional.ofNullable(riskLimit.getMaxLeverage()).orElse(1);
        if (request.targetLeverage() > maxLeverage) {
            log.info("Leverage pre-check rejected: exceeds max leverage. target={} max={} positionId={}",
                    request.targetLeverage(), maxLeverage, request.positionId());
            return new LeveragePrecheckResponse(false, null, maxLeverage, "EXCEEDS_MAX_LEVERAGE");
        }

        RiskSnapshot snapshot = riskSnapshotRepository.findByUserAndInstrument(request.userId(), request.instrumentId())
                .orElse(null);

        BigDecimal equity = firstNonNull(request.equity(), snapshot == null ? null : snapshot.getEquity());
        BigDecimal usedMargin = firstNonNull(request.usedMargin(), snapshot == null ? null : snapshot.getUsedMargin());
        BigDecimal notional = resolveNotional(request, snapshot);

        if (notional == null || notional.signum() <= 0) {
            return new LeveragePrecheckResponse(true, BigDecimal.ZERO, request.targetLeverage(), null);
        }
        if (equity == null || usedMargin == null) {
            log.info("Leverage pre-check bypassed margin validation due to missing equity data. positionId={}"
                    , request.positionId());
            return new LeveragePrecheckResponse(true, BigDecimal.ZERO, request.targetLeverage(), null);
        }

        BigDecimal requiredMargin = notional.divide(BigDecimal.valueOf(request.targetLeverage()), DIVISION_CONTEXT);
        BigDecimal availableMargin = equity.subtract(usedMargin, DIVISION_CONTEXT);
        if (availableMargin.compareTo(requiredMargin) < 0) {
            BigDecimal deficit = requiredMargin.subtract(availableMargin, DIVISION_CONTEXT).max(BigDecimal.ZERO);
            Integer suggestion = suggestLeverage(notional, availableMargin, maxLeverage);
            log.info("Leverage pre-check rejected due to insufficient margin. positionId={} deficit={} suggestion={}"
                    , request.positionId(), deficit, suggestion);
            return new LeveragePrecheckResponse(false, deficit, suggestion, "INSUFFICIENT_MARGIN");
        }
        return new LeveragePrecheckResponse(true, BigDecimal.ZERO, request.targetLeverage(), null);
    }

    private void validate(LeveragePrecheckRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("LeveragePrecheckRequest must not be null");
        }
        if (request.positionId() == null) {
            throw new IllegalArgumentException("positionId is required");
        }
        if (request.instrumentId() == null) {
            throw new IllegalArgumentException("instrumentId is required");
        }
        if (request.userId() == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (request.targetLeverage() == null || request.targetLeverage() <= 0) {
            throw new IllegalArgumentException("targetLeverage must be positive");
        }
    }

    private BigDecimal resolveNotional(LeveragePrecheckRequest request, RiskSnapshot snapshot) {
        BigDecimal notional = computeNotional(request.quantity(), request.markPrice());
        if ((notional == null || notional.signum() == 0) && snapshot != null) {
            notional = snapshot.getNotionalValue();
        }
        return notional;
    }

    private BigDecimal computeNotional(BigDecimal quantity, BigDecimal markPrice) {
        if (quantity == null || markPrice == null) {
            return null;
        }
        BigDecimal absQuantity = quantity.abs();
        BigDecimal absPrice = markPrice.abs();
        if (absQuantity.signum() == 0 || absPrice.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return absPrice.multiply(absQuantity, DIVISION_CONTEXT);
    }

    private BigDecimal firstNonNull(BigDecimal preferred, BigDecimal fallback) {
        return preferred != null ? preferred : fallback;
    }

    private Integer suggestLeverage(BigDecimal notional, BigDecimal availableMargin, Integer maxLeverage) {
        if (notional == null || availableMargin == null || availableMargin.signum() <= 0) {
            return maxLeverage;
        }
        BigDecimal ratio = notional.divide(availableMargin, DIVISION_CONTEXT);
        int suggested = ratio.setScale(0, RoundingMode.DOWN).intValue();
        suggested = Math.max(1, suggested);
        if (maxLeverage != null) {
            suggested = Math.min(suggested, maxLeverage);
        }
        return suggested;
    }
}
