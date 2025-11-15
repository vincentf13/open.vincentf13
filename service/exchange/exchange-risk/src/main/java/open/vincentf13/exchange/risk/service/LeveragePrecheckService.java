package open.vincentf13.exchange.risk.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.risk.config.RiskPreCheckProperties;
import open.vincentf13.exchange.risk.domain.model.RiskLimit;
import open.vincentf13.exchange.risk.infra.persistence.repository.RiskLimitRepository;
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
    private final RiskPreCheckProperties preCheckProperties;

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

        BigDecimal notional = computeNotional(request.quantity(), request.markPrice());
        if (notional == null || notional.signum() <= 0) {
            return new LeveragePrecheckResponse(true, BigDecimal.ZERO, request.targetLeverage(), null);
        }

        BigDecimal requiredMargin = calculateRequiredMargin(notional, request.targetLeverage(), riskLimit);
        BigDecimal marginRatio = requiredMargin.divide(notional, DIVISION_CONTEXT);
        BigDecimal threshold = resolveThreshold(riskLimit);
        if (marginRatio.compareTo(threshold) < 0) {
            Integer suggestion = suggestLeverage(threshold, maxLeverage);
            BigDecimal deficit = threshold.subtract(marginRatio, DIVISION_CONTEXT).max(BigDecimal.ZERO);
            log.info("Leverage pre-check rejected: margin ratio too low. positionId={} ratio={} threshold={} suggestion={}"
                    , request.positionId(), marginRatio, threshold, suggestion);
            return new LeveragePrecheckResponse(false, deficit, suggestion, "MARGIN_RATIO_TOO_LOW");
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

    private BigDecimal calculateRequiredMargin(BigDecimal notional, Integer targetLeverage, RiskLimit riskLimit) {
        BigDecimal leverageBased = notional.divide(BigDecimal.valueOf(targetLeverage), DIVISION_CONTEXT);
        BigDecimal initialRate = Optional.ofNullable(riskLimit.getInitialMarginRate()).orElse(BigDecimal.ZERO);
        BigDecimal rateBased = notional.multiply(initialRate, DIVISION_CONTEXT);
        return leverageBased.max(rateBased);
    }

    private BigDecimal resolveThreshold(RiskLimit riskLimit) {
        BigDecimal maintenance = Optional.ofNullable(riskLimit.getMaintenanceMarginRate()).orElse(BigDecimal.ZERO);
        BigDecimal buffer = Optional.ofNullable(preCheckProperties.getMaintenanceBuffer()).orElse(BigDecimal.ZERO);
        return maintenance.add(buffer, DIVISION_CONTEXT);
    }

    private Integer suggestLeverage(BigDecimal threshold, Integer maxLeverage) {
        if (threshold == null || threshold.signum() <= 0) {
            return maxLeverage;
        }
        BigDecimal allowed = BigDecimal.ONE.divide(threshold, DIVISION_CONTEXT);
        int suggested = allowed.setScale(0, RoundingMode.FLOOR).intValue();
        suggested = Math.max(1, suggested);
        if (maxLeverage != null) {
            suggested = Math.min(suggested, maxLeverage);
        }
        return suggested;
    }
}
