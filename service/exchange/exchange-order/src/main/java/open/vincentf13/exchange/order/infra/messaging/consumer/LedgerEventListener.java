package open.vincentf13.exchange.order.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.infra.messaging.event.FundsFreezeFailedEvent;
import open.vincentf13.exchange.order.infra.messaging.event.FundsFrozenEvent;
import open.vincentf13.exchange.order.infra.messaging.publisher.OrderEventPublisher;
import open.vincentf13.exchange.order.infra.messaging.topic.LedgerTopics;
import open.vincentf13.exchange.order.infra.persistence.repository.OrderRepository;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderStatus;
import open.vincentf13.exchange.order.infra.messaging.handler.OrderFailureHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class LedgerEventListener {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final OrderFailureHandler orderFailureHandler;

    @KafkaListener(
            topics = LedgerTopics.FUNDS_FROZEN,
            groupId = "${open.vincentf13.exchange.order.ledger.consumer-group:exchange-order-ledger}"
    )
    public void onFundsFrozen(@Payload FundsFrozenEvent event, Acknowledgment acknowledgment) {
        try {
            if (event == null || event.orderId() == null) {
                return;
            }
            transactionTemplate.executeWithoutResult(status -> {
                Optional<Order> optional = orderRepository.findById(event.orderId());
                if (optional.isEmpty()) {
                    log.warn("Skip funds frozen event, order not found. orderId={}", event.orderId());
                    return;
                }
                handleFundsFrozen(optional.get(), event);
            });
        } finally {
            acknowledgment.acknowledge();
        }
    }

    @KafkaListener(
            topics = LedgerTopics.FUNDS_FREEZE_FAILED,
            groupId = "${open.vincentf13.exchange.order.ledger.consumer-group:exchange-order-ledger}"
    )
    public void onFundsFreezeFailed(@Payload FundsFreezeFailedEvent event, Acknowledgment acknowledgment) {
        try {
            if (event == null || event.orderId() == null) {
                return;
            }
            orderFailureHandler.markFailed(event.orderId(), "LEDGER_REJECT", event.reason());
        } finally {
            acknowledgment.acknowledge();
        }
    }

    private void handleFundsFrozen(Order order, FundsFrozenEvent event) {
        Instant now = Instant.now();
        int currentVersion = Optional.ofNullable(order.getVersion()).orElse(0);
        boolean updated = orderRepository.updateStatus(order.getOrderId(), order.getUserId(), OrderStatus.ACCEPTED,
                now, currentVersion);
        if (!updated) {
            log.warn("Optimistic lock conflict while marking order accepted. orderId={}", order.getOrderId());
            return;
        }
        order.markStatus(OrderStatus.ACCEPTED, now);
        order.incrementVersion();
        orderEventPublisher.publishOrderCreated(order, event.asset(), event.frozenAmount());
        log.info("Order marked ACCEPTED after funds frozen. orderId={} asset={} amount={}",
                order.getOrderId(), event.asset(), event.frozenAmount());
    }

}
