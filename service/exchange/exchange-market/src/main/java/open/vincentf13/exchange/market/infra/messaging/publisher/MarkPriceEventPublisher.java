package open.vincentf13.exchange.market.infra.messaging.publisher;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.market.domain.model.MarkPriceSnapshot;
import open.vincentf13.exchange.market.infra.MarketEvent;
import open.vincentf13.exchange.market.mq.event.MarkPriceUpdatedEvent;
import open.vincentf13.exchange.market.mq.topic.MarketTopics;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.infra.mysql.mq.outbox.MqOutboxRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarkPriceEventPublisher {

  private final MqOutboxRepository outboxRepository;

  public void publishMarkPriceUpdated(MarkPriceSnapshot snapshot) {
    if (snapshot == null || snapshot.getInstrumentId() == null) {
      return;
    }
    MarkPriceUpdatedEvent event =
        new MarkPriceUpdatedEvent(
            snapshot.getInstrumentId(),
            snapshot.getMarkPrice(),
            snapshot.getTradeId(),
            snapshot.getTradeExecutedAt(),
            snapshot.getCalculatedAt());
    outboxRepository.append(
        MarketTopics.MARK_PRICE_UPDATED.getTopic(), snapshot.getInstrumentId(), event, null);
    OpenLog.debug(
        MarketEvent.MARK_PRICE_OUTBOX_APPENDED,
        "instrumentId",
        snapshot.getInstrumentId(),
        "tradeId",
        snapshot.getTradeId());
  }
}
