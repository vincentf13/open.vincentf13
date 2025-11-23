package open.vincentf13.exchange.market.mq.topic;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.market.mq.event.MarkPriceUpdatedEvent;

@Getter
@RequiredArgsConstructor
public enum MarketTopics {
    MARK_PRICE_UPDATED("market.mark-price", MarkPriceUpdatedEvent.class);

    private final String topic;
    private final Class<?> eventType;
}
