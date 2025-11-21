package open.vincentf13.exchange.marketdata.infra.messaging.publisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.market.mq.event.MarkPriceUpdatedEvent;
import open.vincentf13.exchange.market.mq.topic.MarketTopics;
import open.vincentf13.exchange.marketdata.domain.model.MarkPriceSnapshot;
import open.vincentf13.sdk.infra.mysql.mq.outbox.MqOutboxRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarkPriceEventPublisher {

    private final MqOutboxRepository outboxRepository;

    public void publishMarkPriceUpdated(MarkPriceSnapshot snapshot) {
        if (snapshot == null || snapshot.getInstrumentId() == null) {
            return;
        }
        MarkPriceUpdatedEvent event = new MarkPriceUpdatedEvent(
                snapshot.getInstrumentId(),
                snapshot.getMarkPrice(),
                snapshot.getTradeId(),
                snapshot.getTradeExecutedAt(),
                snapshot.getCalculatedAt()
        );
        outboxRepository.append(MarketTopics.MARK_PRICE_UPDATED,
                snapshot.getInstrumentId(),
                event,
                null);
        log.debug("MarkPriceUpdated appended to outbox for instrument {}", snapshot.getInstrumentId());
    }
}
