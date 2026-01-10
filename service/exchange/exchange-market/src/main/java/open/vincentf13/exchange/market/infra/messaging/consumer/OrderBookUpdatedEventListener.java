package open.vincentf13.exchange.market.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.market.infra.MarketEvent;
import open.vincentf13.exchange.market.infra.cache.OrderBookCacheService;
import open.vincentf13.exchange.matching.sdk.mq.event.OrderBookUpdatedEvent;
import open.vincentf13.exchange.matching.sdk.mq.topic.MatchingTopics;
import open.vincentf13.sdk.core.validator.OpenValidator;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class OrderBookUpdatedEventListener {
    
    private final OrderBookCacheService orderBookCacheService;
    
    @KafkaListener(
            topics = MatchingTopics.Names.ORDERBOOK_UPDATED,
            groupId = "${open.vincentf13.exchange.market.orderbook.consumer-group:exchange-market-orderbook}"
    )
    public void onOrderBookUpdated(@Payload OrderBookUpdatedEvent event,
                                   Acknowledgment acknowledgment) {
        try {
            OpenValidator.validateOrThrow(event);
        } catch (Exception e) {
            OpenLog.warn(MarketEvent.ORDERBOOK_PAYLOAD_INVALID, e, "event", event);
            acknowledgment.acknowledge();
            return;
        }
        try {
            orderBookCacheService.applyUpdates(
                    event.instrumentId(),
                    event.updates(),
                    event.updatedAt() == null ? Instant.now() : event.updatedAt()
            );
            acknowledgment.acknowledge();
        } catch (Exception ex) {
            OpenLog.error(MarketEvent.ORDERBOOK_APPLY_FAILED, ex,
                          "instrumentId", event.instrumentId());
        }
    }
}
