package open.vincentf13.exchange.risk.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.market.mq.event.MarkPriceUpdatedEvent;
import open.vincentf13.exchange.market.mq.topic.MarketTopics;
import open.vincentf13.exchange.risk.infra.cache.MarkPriceCache;
import open.vincentf13.sdk.core.OpenValidator;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class MarkPriceEventListener implements ConsumerSeekAware {

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

    @KafkaListener(topics = MarketTopics.Names.MARK_PRICE_UPDATED, groupId = "${spring.kafka.consumer.group-id:exchange-risk}")
    public void onMarkPriceUpdated(@Payload MarkPriceUpdatedEvent event) {
        OpenValidator.validateOrThrow(event);
        markPriceCache.put(event.instrumentId(), event.markPrice());
    }
}
