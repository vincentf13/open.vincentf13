package open.vincentf13.exchange.position.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.market.mq.event.MarkPriceUpdatedEvent;
import open.vincentf13.exchange.market.mq.topic.MarketTopics;
import open.vincentf13.exchange.position.domain.service.PositionDomainService;
import open.vincentf13.exchange.position.infra.cache.MarkPriceCache;
import open.vincentf13.exchange.position.infra.PositionEvent;
import open.vincentf13.sdk.core.OpenValidator;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarkPriceListener {

    private final PositionDomainService positionDomainService;
    private final MarkPriceCache markPriceCache;

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
