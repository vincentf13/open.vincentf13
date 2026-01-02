package open.vincentf13.exchange.risk.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.market.mq.event.MarkPriceUpdatedEvent;
import open.vincentf13.exchange.market.mq.topic.MarketTopics;
import open.vincentf13.exchange.risk.infra.cache.MarkPriceCache;
import open.vincentf13.sdk.core.OpenValidator;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class MarkPriceEventListener {

    private final MarkPriceCache markPriceCache;

    @KafkaListener(topics = MarketTopics.Names.MARK_PRICE_UPDATED, groupId = "${spring.kafka.consumer.group-id:exchange-risk}")
    public void onMarkPriceUpdated(@Payload MarkPriceUpdatedEvent event) {
        OpenValidator.validateOrThrow(event);
        markPriceCache.put(event.instrumentId(), event.markPrice());
    }
}
