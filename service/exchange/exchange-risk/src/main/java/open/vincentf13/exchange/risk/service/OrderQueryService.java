package open.vincentf13.exchange.risk.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.order.mq.event.OrderSubmittedEvent;
import open.vincentf13.exchange.risk.config.RiskPreCheckProperties;
import open.vincentf13.exchange.risk.domain.model.RiskLimit;
import open.vincentf13.exchange.risk.domain.model.RiskSnapshot;
import open.vincentf13.exchange.risk.infra.persistence.repository.RiskLimitRepository;
import open.vincentf13.exchange.risk.infra.persistence.repository.RiskSnapshotRepository;
import open.vincentf13.exchange.risk.margin.sdk.mq.event.MarginPreCheckFailedEvent;
import open.vincentf13.exchange.risk.margin.sdk.mq.topic.RiskTopics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderQueryService {

    private static final MathContext DIVISION_CONTEXT = new MathContext(18, RoundingMode.HALF_UP);

    private final RiskSnapshotRepository riskSnapshotRepository;
    private final RiskLimitRepository riskLimitRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RiskPreCheckProperties preCheckProperties;

    public void preCheck(OrderSubmittedEvent event) {
        if (!isEventValid(event)) {
            log.warn("Skip OrderSubmitted due to invalid payload: {}", event);
            return;
        }
        FailureReason failureReason = evaluate(event);

        if (failureReason != null) {
            publishFailure(event, failureReason);
            return;
        } else {
            // 成功事件
        }
        log.info("Risk pre-check passed. orderId={} userId={} instrumentId={} quantity={} price={}",
                 event.orderId(), event.userId(), event.instrumentId(), event.quantity(), event.price());
    }

    private FailureReason evaluate(OrderSubmittedEvent event) {

       // 取得風控基準
        RiskLimit riskLimit = riskLimitRepository.findEffective(event.instrumentId(), null, Instant.now())
                .orElse(null);
        if (riskLimit == null) {
            return FailureReason.RISK_LIMIT_NOT_FOUND;
        }
        RiskSnapshot snapshot = riskSnapshotRepository.findByUserAndInstrument(event.userId(), event.instrumentId())
                .orElse(null);
        if (snapshot == null) {
            return FailureReason.SNAPSHOT_NOT_FOUND;
        }
        // 如快照時間落後 `OrderSubmitted` 事件時間，則即時呼叫 `position` 取得最新倉位

        // 鎖定 snapshot

        // 驗證槓桿
        BigDecimal leverage = new BigDecimal(Optional.ofNullable(riskLimit.getMaxLeverage()).orElse(1));

        // 計算委託名義價值
        BigDecimal orderPrice = resolvePrice(event);
        if (orderPrice == null || orderPrice.signum() <= 0) {
            return FailureReason.MARK_PRICE_UNAVAILABLE;
        }
        BigDecimal quantity = Optional.ofNullable(event.quantity()).orElse(BigDecimal.ZERO);
        if (quantity.signum() <= 0) {
            return FailureReason.INVALID_QUANTITY;
        }
        BigDecimal orderNotional = orderPrice.multiply(quantity, DIVISION_CONTEXT);
        // 試算預扣保證金
        BigDecimal requiredMarginByLeverage = orderNotional.divide(leverage, DIVISION_CONTEXT);
        BigDecimal initialMarginRate = Optional.ofNullable(riskLimit.getInitialMarginRate()).orElse(BigDecimal.ZERO);
        BigDecimal requiredMarginByRate = orderNotional.multiply(initialMarginRate, DIVISION_CONTEXT);
        BigDecimal requiredMargin = requiredMarginByLeverage.max(requiredMarginByRate);


        // 取得 `equity`
        BigDecimal equity = Optional.ofNullable(snapshot.getEquity()).orElse(BigDecimal.ZERO);

        // 保證金餘額效驗
        BigDecimal usedMargin = Optional.ofNullable(snapshot.getUsedMargin()).orElse(BigDecimal.ZERO);
        BigDecimal availableMargin = equity.subtract(usedMargin, DIVISION_CONTEXT);
        if (availableMargin.compareTo(requiredMargin) < 0) {
            return FailureReason.INSUFFICIENT_MARGIN;
        }

        // 計算下單後指標
        BigDecimal existingNotional = Optional.ofNullable(snapshot.getNotionalValue()).orElse(BigDecimal.ZERO);
        BigDecimal simulatedNotional = existingNotional.add(orderNotional, DIVISION_CONTEXT);
        if (simulatedNotional.signum() == 0) {
            return null;
        }
        BigDecimal simulatedMarginRatio = equity.divide(simulatedNotional, DIVISION_CONTEXT);
        BigDecimal maintenance = Optional.ofNullable(riskLimit.getMaintenanceMarginRate()).orElse(BigDecimal.ZERO);
        BigDecimal buffer = Optional.ofNullable(preCheckProperties.getMaintenanceBuffer()).orElse(BigDecimal.ZERO);
        BigDecimal threshold = maintenance.add(buffer, DIVISION_CONTEXT);
        if (simulatedMarginRatio.compareTo(threshold) < 0) {
            return FailureReason.MARGIN_RATIO_TOO_LOW;
        }

        return null;
    }

    private BigDecimal resolvePrice(OrderSubmittedEvent event) {
        // 限價單
        if (event.price() != null && event.price().signum() > 0) {
            return event.price();
        }

        // 市價單
        // 市價單暫時無同步行情來源，回傳 null 以觸發 MARK_PRICE_UNAVAILABLE，後續可串接 market-data REST/API。
        return null;
    }

    private boolean isEventValid(OrderSubmittedEvent event) {
        if (event == null) {
            return false;
        }
        return event.orderId() != null
                && event.userId() != null
                && event.instrumentId() != null
                && event.quantity() != null;
    }

    private void publishFailure(OrderSubmittedEvent event, FailureReason reason) {
        MarginPreCheckFailedEvent payload = new MarginPreCheckFailedEvent(event.orderId(), reason.name());
        String key = event.orderId() == null ? null : event.orderId().toString();
        kafkaTemplate.send(RiskTopics.MARGIN_PRECHECK_FAILED, key, payload);
        log.warn("Risk pre-check failed. orderId={} reason={}", event.orderId(), reason.name());
    }

    private enum FailureReason {
        INVALID_QUANTITY,
        RISK_LIMIT_NOT_FOUND,
        SNAPSHOT_NOT_FOUND,
        MARK_PRICE_UNAVAILABLE,
        INSUFFICIENT_MARGIN,
        MARGIN_RATIO_TOO_LOW
    }
}
