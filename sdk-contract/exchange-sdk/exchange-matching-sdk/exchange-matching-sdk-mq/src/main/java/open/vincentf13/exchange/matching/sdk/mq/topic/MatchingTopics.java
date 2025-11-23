package open.vincentf13.exchange.matching.sdk.mq.topic;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.matching.sdk.mq.event.OrderBookUpdatedEvent;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;

@Getter
@RequiredArgsConstructor
public enum MatchingTopics {
    TRADE_EXECUTED("matching.trade-executed", TradeExecutedEvent.class),
    ORDERBOOK_UPDATED("matching.orderbook-updated", OrderBookUpdatedEvent.class);

    private final String topic;
    private final Class<?> eventType;
}
