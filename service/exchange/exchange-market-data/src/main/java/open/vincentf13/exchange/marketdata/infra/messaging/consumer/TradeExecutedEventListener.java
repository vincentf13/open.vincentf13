package open.vincentf13.exchange.marketdata.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.marketdata.infra.cache.TickerStatsCacheService;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.matching.sdk.mq.topic.MatchingTopics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TradeExecutedEventListener {

    private final TickerStatsCacheService tickerStatsCacheService;

    @KafkaListener(
            topics = MatchingTopics.TRADE_EXECUTED,
            groupId = "${open.vincentf13.exchange.marketdata.trade.consumer-group:exchange-market-data-trade}"
    )
    public void onTradeExecuted(@Payload TradeExecutedEvent event, Acknowledgment acknowledgment) {
        if (event == null || event.instrumentId() == null) {
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
