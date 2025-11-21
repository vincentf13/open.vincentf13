package open.vincentf13.exchange.marketdata.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.market.sdk.rest.api.dto.OrderBookLevel;
import open.vincentf13.exchange.marketdata.infra.cache.OrderBookCacheService;
import open.vincentf13.exchange.matching.sdk.mq.event.OrderBookUpdatedEvent;
import open.vincentf13.exchange.matching.sdk.mq.topic.MatchingTopics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderBookUpdatedEventListener {

    private final OrderBookCacheService orderBookCacheService;

    @KafkaListener(
            topics = MatchingTopics.ORDERBOOK_UPDATED,
            groupId = "${open.vincentf13.exchange.marketdata.orderbook.consumer-group:exchange-market-data-orderbook}"
    )
    public void onOrderBookUpdated(@Payload OrderBookUpdatedEvent event, Acknowledgment acknowledgment) {
        if (event == null || event.instrumentId() == null) {
            acknowledgment.acknowledge();
            return;
        }
        try {
            List<OrderBookLevel> bids = mapLevels(event.bids());
            List<OrderBookLevel> asks = mapLevels(event.asks());
            orderBookCacheService.update(
                    event.instrumentId(),
                    bids,
                    asks,
                    event.bestBid(),
                    event.bestAsk(),
                    event.midPrice(),
                    event.updatedAt() == null ? Instant.now() : event.updatedAt()
            );
            acknowledgment.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to apply OrderBookUpdated event for instrument {}", event.instrumentId(), ex);
        }
    }

    private List<OrderBookLevel> mapLevels(List<OrderBookUpdatedEvent.OrderBookLevel> levels) {
        if (levels == null) {
            return List.of();
        }
        return levels.stream()
                .filter(Objects::nonNull)
                .map(level -> OrderBookLevel.builder()
                        .price(level.price())
                        .quantity(level.quantity())
                        .build())
                .collect(Collectors.toList());
    }
}
