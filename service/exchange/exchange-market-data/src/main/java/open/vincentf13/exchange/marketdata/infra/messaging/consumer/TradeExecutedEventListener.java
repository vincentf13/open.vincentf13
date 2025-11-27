package open.vincentf13.exchange.marketdata.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.marketdata.infra.MarketDataEvent;
import open.vincentf13.exchange.marketdata.infra.cache.TickerStatsCacheService;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.matching.sdk.mq.topic.MatchingTopics;
import open.vincentf13.sdk.core.OpenValidator;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TradeExecutedEventListener {

    private final TickerStatsCacheService tickerStatsCacheService;

    @KafkaListener(
            topics = MatchingTopics.Names.TRADE_EXECUTED,
            groupId = "${open.vincentf13.exchange.marketdata.trade.consumer-group:exchange-market-data-trade}"
    )
    public void onTradeExecuted(@Payload TradeExecutedEvent event, Acknowledgment acknowledgment) {
        try {
            OpenValidator.validateOrThrow(event);
        } catch (Exception e) {
            OpenLog.warn(MarketDataEvent.TRADE_EVENT_PAYLOAD_INVALID, e, "event", event);
            acknowledgment.acknowledge();
            return;
        }
        tickerStatsCacheService.recordTrade(
                event.instrumentId(),
                event.tradeId(),
                event.price(),
                event.quantity(),
                event.executedAt());
        acknowledgment.acknowledge();
    }
}
