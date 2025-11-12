package open.vincentf13.exchange.order.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.matching.sdk.mq.topic.MatchingTopics;
import open.vincentf13.exchange.order.infra.persistence.repository.OrderRepository;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderStatus;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class TradeExecutedEventListener {

    private final OrderRepository orderRepository;
    private final TransactionTemplate transactionTemplate;

    @KafkaListener(
            topics = MatchingTopics.TRADE_EXECUTED,
            groupId = "${open.vincentf13.exchange.order.matching.consumer-group:exchange-order-matching}"
    )
    public void onTradeExecuted(@Payload TradeExecutedEvent event, Acknowledgment acknowledgment) {
        if (!isValid(event)) {
            acknowledgment.acknowledge();
            return;
        }
        transactionTemplate.executeWithoutResult(status ->
                orderRepository.findById(event.orderId())
                        .ifPresentOrElse(order -> handleTrade(order, event),
                                () -> log.warn("Skip TradeExecuted, order not found. orderId={} tradeId={}",
                                        event.orderId(), event.tradeId())));
        acknowledgment.acknowledge();
    }

    private boolean isValid(TradeExecutedEvent event) {
        if (event == null) {
            return false;
        }
        if (event.orderId() == null || event.tradeId() == null) {
            log.warn("Invalid TradeExecuted event: {}", event);
            return false;
        }
        BigDecimal quantity = event.quantity();
        if (quantity == null || quantity.signum() <= 0) {
            log.warn("Ignore TradeExecuted with non-positive quantity. tradeId={} orderId={}",
                    event.tradeId(), event.orderId());
            return false;
        }
        return true;
    }

    private void handleTrade(Order order, TradeExecutedEvent event) {
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.REJECTED) {
            log.warn("Ignore TradeExecuted for closed order. orderId={} status={} tradeId={}",
                    order.getOrderId(), order.getStatus(), event.tradeId());
            return;
        }
        int currentVersion = Optional.ofNullable(order.getVersion()).orElse(0);
        order.applyTrade(event.quantity(), event.price(), event.fee(), event.executedAt());
        boolean updated = orderRepository.updateAfterTrade(order, order.getUpdatedAt(), currentVersion);
        if (!updated) {
            log.warn("Optimistic lock conflict when applying trade. orderId={} tradeId={} version={}",
                    order.getOrderId(), event.tradeId(), currentVersion);
            return;
        }
        order.incrementVersion();
        log.info("Order trade applied. orderId={} tradeId={} filled={} remaining={} status={}", order.getOrderId(),
                event.tradeId(), order.getFilledQuantity(), order.getRemainingQuantity(), order.getStatus());
    }
}
