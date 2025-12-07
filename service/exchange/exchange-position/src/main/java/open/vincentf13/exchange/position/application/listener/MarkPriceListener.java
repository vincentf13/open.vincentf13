package open.vincentf13.exchange.position.application.listener;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.market.mq.event.MarkPriceUpdatedEvent;
import open.vincentf13.exchange.market.mq.topic.MarketTopics;
import open.vincentf13.exchange.position.domain.service.PositionDomainService;
import open.vincentf13.exchange.position.infra.cache.MarkPriceCache;
import open.vincentf13.sdk.core.OpenValidator;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarkPriceListener {

    private final PositionDomainService positionDomainService;
    private final MarkPriceCache markPriceCache;

    @KafkaListener(topics = MarketTopics.Names.MARK_PRICE_UPDATED)
    public void onMarkPriceUpdated(@Payload MarkPriceUpdatedEvent event) {
        OpenValidator.validateOrThrow(event);

        markPriceCache.update(event.instrumentId(), event.markPrice(), event.calculatedAt());
        positionDomainService.updateMarkPrice(event.instrumentId(), event.markPrice());
    }
}
