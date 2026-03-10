package open.vincentf13.exchange.matching.sdk.mq.topic;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.matching.sdk.mq.event.OrderBookUpdatedEvent;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;

@Getter
@RequiredArgsConstructor
public enum MatchingTopics {
  TRADE_EXECUTED(Names.TRADE_EXECUTED, TradeExecutedEvent.class),
  ORDERBOOK_UPDATED(Names.ORDERBOOK_UPDATED, OrderBookUpdatedEvent.class);

  private final String topic;
  private final Class<?> eventType;

  public static final class Names {
    public static final String TRADE_EXECUTED = "matching.trade-executed";
    public static final String ORDERBOOK_UPDATED = "matching.orderbook-updated";

    private Names() {}
  }
}
