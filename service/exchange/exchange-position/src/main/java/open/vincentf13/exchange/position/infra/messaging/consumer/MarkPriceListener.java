package open.vincentf13.exchange.position.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.market.mq.event.MarkPriceUpdatedEvent;
import open.vincentf13.exchange.market.mq.topic.MarketTopics;
import open.vincentf13.exchange.position.domain.service.PositionDomainService;
import open.vincentf13.exchange.position.infra.cache.MarkPriceCache;
import open.vincentf13.sdk.core.validator.OpenValidator;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class MarkPriceListener {

    private final PositionDomainService positionDomainService;
    private final MarkPriceCache markPriceCache;
    
    private final Map<Long, TriggerState> triggerStateMap = new ConcurrentHashMap<>();

    private record TriggerState(BigDecimal price, Instant triggeredAt) {}

    @KafkaListener(topics = MarketTopics.Names.MARK_PRICE_UPDATED,
                   groupId = "${exchange.position.mark-price.consumer-group:exchange-position-mark-price}")
    public void onMarkPriceUpdated(@Payload MarkPriceUpdatedEvent event,
                                   Acknowledgment acknowledgment) {
        OpenValidator.validateOrThrow(event);
        markPriceCache.update(event.instrumentId(), event.markPrice(), event.calculatedAt());
        
        if (shouldTriggerUpdate(event.instrumentId(), event.markPrice())) {
            // 跟前一次的cache 價差超過 0.1% 才執行 (10秒頻率控制在領域服務中按倉位處理)
            positionDomainService.updateMarkPrice(event.instrumentId(), event.markPrice());
            triggerStateMap.put(event.instrumentId(), new TriggerState(event.markPrice(), Instant.now()));
        }
        acknowledgment.acknowledge();
    }

    private boolean shouldTriggerUpdate(Long instrumentId, BigDecimal newPrice) {
        TriggerState lastState = triggerStateMap.get(instrumentId);
        if (lastState == null) {
            return true;
        }

        // Price check: > 0.1%
        BigDecimal oldPrice = lastState.price();
        if (oldPrice.compareTo(BigDecimal.ZERO) == 0) {
            return true;
        }
        
        BigDecimal diff = newPrice.subtract(oldPrice).abs();
        BigDecimal threshold = oldPrice.multiply(new BigDecimal("0.001"));
        
        return diff.compareTo(threshold) > 0;
    }
}
