package open.vincentf13.exchange.position.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.market.mq.event.MarkPriceUpdatedEvent;
import open.vincentf13.exchange.market.mq.topic.MarketTopics;
import open.vincentf13.exchange.position.domain.service.PositionDomainService;
import open.vincentf13.exchange.position.infra.cache.MarkPriceCache;
import open.vincentf13.exchange.position.infra.PositionEvent;
import open.vincentf13.sdk.core.OpenValidator;
import open.vincentf13.sdk.core.log.OpenLog;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class MarkPriceListener implements ConsumerSeekAware {

    private final PositionDomainService positionDomainService;
    private final MarkPriceCache markPriceCache;

    /**
     調試用
     * @param assignments
     * @param callback
     */
    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        callback.seekToBeginning(assignments.keySet());
    }

    @KafkaListener(topics = MarketTopics.Names.MARK_PRICE_UPDATED,
                   groupId = "${exchange.position.mark-price.consumer-group:exchange-position-mark-price}")
    public void onMarkPriceUpdated(@Payload MarkPriceUpdatedEvent event,
                                   Acknowledgment acknowledgment) {
        try {
            OpenValidator.validateOrThrow(event);
            markPriceCache.update(event.instrumentId(), event.markPrice(), event.calculatedAt());
            positionDomainService.updateMarkPrice(event.instrumentId(), event.markPrice());
            acknowledgment.acknowledge();
        } catch (Exception e) {
            OpenLog.error(PositionEvent.POSITION_MARK_PRICE_UPDATE_FAILED, e, "event", event);
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }
}
