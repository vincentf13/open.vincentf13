package open.vincentf13.exchange.order.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.infra.messaging.publisher.OrderEventPublisher;
import open.vincentf13.exchange.order.infra.persistence.repository.OrderRepository;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderStatus;
import open.vincentf13.exchange.position.sdk.mq.event.PositionReserveRejectedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionReservedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionTopics;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.exchange.order.infra.OrderEventEnum;
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
public class PositionReserveEventListener {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final TransactionTemplate transactionTemplate;

    @KafkaListener(
            topics = PositionTopics.POSITION_RESERVED,
            groupId = "${exchange.order.position.consumer-group:exchange-order-position}"
    )
    public void onPositionReserved(@Payload PositionReservedEvent event, Acknowledgment acknowledgment) {
        if (event == null) {
            acknowledgment.acknowledge();
            return;
        }
        transactionTemplate.executeWithoutResult(status -> handleReservationSuccess(event));
        acknowledgment.acknowledge();
    }

    @KafkaListener(
            topics = PositionTopics.POSITION_RESERVE_REJECTED,
            groupId = "${exchange.order.position.consumer-group:exchange-order-position}"
    )
    public void onPositionReserveRejected(@Payload PositionReserveRejectedEvent event, Acknowledgment acknowledgment) {
        if (event == null) {
            acknowledgment.acknowledge();
            return;
        }
        transactionTemplate.executeWithoutResult(status -> handleReservationFailure(event));
        acknowledgment.acknowledge();
    }

    private void handleReservationSuccess(PositionReservedEvent event) {
        Instant now = Instant.now();
        Order updateRecord = Order.builder()
                .status(OrderStatus.SUBMITTED)
                .submittedAt(now)
                .closeCostPrice(event.avgOpenPrice())
                .build();
        boolean updated = orderRepository.updateSelectiveBy(
                updateRecord,
                event.orderId(),
                event.userId(),
                null,
                OrderStatus.PENDING);
        if (!updated) {
            OpenLog.warn(log, OrderEventEnum.ORDER_STATUS_CONFLICT, null,
                    "orderId", event.orderId());
            return;
        }
        Optional<Order> optionalOrder = orderRepository.findOne(Order.builder().orderId(event.orderId()).build());
        if (optionalOrder.isEmpty()) {
            OpenLog.warn(log, OrderEventEnum.ORDER_NOT_FOUND_AFTER_RESERVE, null,
                    "orderId", event.orderId());
            return;
        }
        Order order = optionalOrder.get();
        order.markStatus(OrderStatus.SUBMITTED, now);
        order.setSubmittedAt(now);
        order.setCloseCostPrice(event.avgOpenPrice());
        orderEventPublisher.publishOrderSubmitted(order);
        OpenLog.info(log, OrderEventEnum.ORDER_POSITION_RESERVED, "orderId", order.getOrderId());
    }

    private void handleReservationFailure(PositionReserveRejectedEvent event) {
        Instant now = Instant.now();
        Order updateRecord = Order.builder()
                .status(OrderStatus.FAILED)
                .build();
        boolean updated = orderRepository.updateSelectiveBy(
                updateRecord,
                event.orderId(),
                event.userId(),
                null,
                OrderStatus.PENDING);
        if (!updated) {
            OpenLog.warn(log, OrderEventEnum.ORDER_STATUS_CONFLICT, null,
                    "orderId", event.orderId());
            return;
        }
        Optional<Order> optionalOrder = orderRepository.findOne(Order.builder().orderId(event.orderId()).build());
        if (optionalOrder.isPresent()) {
            Order order = optionalOrder.get();
            order.markStatus(OrderStatus.FAILED, now);
            OpenLog.warn(log, OrderEventEnum.ORDER_POSITION_RESERVE_REJECTED, null,
                    "orderId", order.getOrderId(),
                    "reason", event.reason());
            return;
        }
        OpenLog.warn(log, OrderEventEnum.ORDER_NOT_FOUND_AFTER_RESERVE_REJECT, null,
                "orderId", event.orderId(),
                "reason", event.reason());
    }
}
