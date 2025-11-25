package open.vincentf13.exchange.order.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.sdk.mq.event.FundsFreezeFailedEvent;
import open.vincentf13.exchange.account.ledger.sdk.mq.event.FundsFrozenEvent;
import open.vincentf13.exchange.account.ledger.sdk.mq.topic.LedgerTopics;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.infra.OrderEventEnum;
import open.vincentf13.exchange.order.infra.messaging.handler.OrderFailureHandler;
import open.vincentf13.exchange.order.infra.messaging.publisher.OrderEventPublisher;
import open.vincentf13.exchange.order.infra.persistence.repository.OrderRepository;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderStatus;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class LedgerEventListener {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final OrderFailureHandler orderFailureHandler;

    @KafkaListener(
            topics = LedgerTopics.Names.FUNDS_FROZEN,
            groupId = "${open.vincentf13.exchange.order.ledger.consumer-group:exchange-order-ledger}"
    )
    public void onFundsFrozen(@Payload FundsFrozenEvent event, Acknowledgment acknowledgment) {
        if (event == null) {
            acknowledgment.acknowledge();
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            Optional<Order> optional = orderRepository.findOne(Order.builder().orderId(event.orderId()).build());
            if (optional.isEmpty()) {
                OpenLog.warn(OrderEventEnum.ORDER_FUNDS_FROZEN_SKIP_NOT_FOUND,
                        "orderId", event.orderId());
                return;
            }
            handleFundsFrozen(optional.get(), event);
        });
        acknowledgment.acknowledge();
    }

    @KafkaListener(
            topics = LedgerTopics.Names.FUNDS_FREEZE_FAILED,
            groupId = "${open.vincentf13.exchange.order.ledger.consumer-group:exchange-order-ledger}"
    )
    public void onFundsFreezeFailed(@Payload FundsFreezeFailedEvent event, Acknowledgment acknowledgment) {
        if (event == null) {
            acknowledgment.acknowledge();
            return;
        }
        orderFailureHandler.markFailed(event.orderId(), "LEDGER_REJECT", event.reason());
        acknowledgment.acknowledge();
    }

    private void handleFundsFrozen(Order order, FundsFrozenEvent event) {
        Instant now = Instant.now();
        int currentVersion = Optional.ofNullable(order.getVersion()).orElse(0);
        Order updateRecord = Order.builder()
                .status(OrderStatus.ACCEPTED)
                .submittedAt(now)
                .version(currentVersion + 1)
                .build();
        boolean updated = orderRepository.updateSelectiveBy(updateRecord, order.getOrderId(), order.getUserId(), currentVersion, null);
        if (!updated) {
            OpenLog.warn(OrderEventEnum.ORDER_FUNDS_FROZEN_LOCK_CONFLICT,
                    "orderId", order.getOrderId());
            return;
        }
        order.markStatus(OrderStatus.ACCEPTED, now);
        order.setSubmittedAt(now);
        order.incrementVersion();
        orderEventPublisher.publishOrderCreated(order, event.asset(), event.frozenAmount());
        OpenLog.info(OrderEventEnum.ORDER_MARK_ACCEPTED,
                "orderId", order.getOrderId(),
                "asset", event.asset(),
                "amount", event.frozenAmount());
    }

}
