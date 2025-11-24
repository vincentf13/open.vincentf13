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
import open.vincentf13.sdk.core.OpenLog;
import open.vincentf13.sdk.core.OpenValidator;
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
        OpenValidator.validateOrThrow(event);
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
        OpenValidator.validateOrThrow(event);
        transactionTemplate.executeWithoutResult(status -> handleReservationFailure(event));
        acknowledgment.acknowledge();
    }

    private void handleReservationSuccess(PositionReservedEvent event) {
        Instant now = Instant.now();
        boolean updated = orderRepository.updateStatusAndCost(
                event.orderId(),
                event.userId(),
                OrderStatus.PENDING,
                OrderStatus.SUBMITTED,
                now,
                null,
                event.avgOpenPrice()
        );
        if (!updated) {
            OpenLog.warn(log, "OrderStatusConflict", "Failed to update order status on position reserve", null,
                    "orderId", event.orderId());
            return;
        }
        Optional<Order> optionalOrder = orderRepository.findById(event.orderId());
        if (optionalOrder.isEmpty()) {
            OpenLog.warn(log, "OrderNotFoundAfterReserve", "Order updated but not found for event publish", null,
                    "orderId", event.orderId());
            return;
        }
        Order order = optionalOrder.get();
        order.markStatus(OrderStatus.SUBMITTED, now);
        order.setSubmittedAt(now);
        orderEventPublisher.publishOrderSubmitted(order);
        OpenLog.info(log, "OrderPositionReserved", "Position reserved for order", "orderId", order.getOrderId());
    }

    private void handleReservationFailure(PositionReserveRejectedEvent event) {
        Instant now = Instant.now();
        boolean updated = orderRepository.updateStatusByCurrentStatus(
                event.orderId(),
                event.userId(),
                OrderStatus.PENDING,
                OrderStatus.FAILED,
                null,
                null
        );
        if (!updated) {
            OpenLog.warn(log, "OrderStatusConflict", "Failed to mark order failed on position reserve rejection", null,
                    "orderId", event.orderId());
            return;
        }
        Optional<Order> optionalOrder = orderRepository.findById(event.orderId());
        if (optionalOrder.isPresent()) {
            Order order = optionalOrder.get();
            order.markStatus(OrderStatus.FAILED, now);
            OpenLog.warn(log, "OrderPositionReserveRejected", "Position reserve rejected", null,
                    "orderId", order.getOrderId(),
                    "reason", event.reason());
            return;
        }
        OpenLog.warn(log, "OrderNotFoundAfterReserveReject", "Order updated but not found for reject log", null,
                "orderId", event.orderId(),
                "reason", event.reason());
    }
}
