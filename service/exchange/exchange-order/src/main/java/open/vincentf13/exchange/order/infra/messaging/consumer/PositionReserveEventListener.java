package open.vincentf13.exchange.order.infra.messaging.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.infra.messaging.publisher.OrderEventPublisher;
import open.vincentf13.exchange.order.infra.persistence.repository.OrderRepository;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderStatus;
import open.vincentf13.exchange.position.sdk.mq.event.PositionReserveRejectedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionReservedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionTopics;
import open.vincentf13.sdk.core.log.OpenLog;
import open.vincentf13.sdk.core.exception.OpenServiceException;
import org.springframework.kafka.annotation.KafkaListener;
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
    public void onPositionReserved(@Payload PositionReservedEvent event) {
        if (event == null) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            orderRepository.findById(event.orderId())
                    .ifPresent(order -> handleReservationSuccess(order, event));
        });
    }

    @KafkaListener(
            topics = PositionTopics.POSITION_RESERVE_REJECTED,
            groupId = "${exchange.order.position.consumer-group:exchange-order-position}"
    )
    public void onPositionReserveRejected(@Payload PositionReserveRejectedEvent event) {
        if (event == null) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            orderRepository.findById(event.orderId())
                    .ifPresent(order -> handleReservationFailure(order, event.reason()));
        });
    }

    private void handleReservationSuccess(Order order, PositionReservedEvent event) {
        Instant now = Instant.now();
        boolean updated = orderRepository.updateStatus(order.getOrderId(), order.getUserId(), OrderStatus.ACCEPTED, now,
                Optional.ofNullable(order.getVersion()).orElse(0));
        if (!updated) {
            OpenLog.warn(log, "OrderStatusConflict", "Failed to update order status on position reserve", null,
                    "orderId", order.getOrderId());
            return;
        }
        order.markStatus(OrderStatus.ACCEPTED, now);
        order.incrementVersion();
        orderEventPublisher.publishOrderSubmitted(order);
        OpenLog.info(log, "OrderPositionReserved", "Position reserved for order", "orderId", order.getOrderId());
    }

    private void handleReservationFailure(Order order, String reason) {
        Instant now = Instant.now();
        boolean updated = orderRepository.updateStatus(order.getOrderId(), order.getUserId(), OrderStatus.FAILED, now,
                Optional.ofNullable(order.getVersion()).orElse(0));
        if (!updated) {
            OpenLog.warn(log, "OrderStatusConflict", "Failed to mark order failed on position reserve rejection", null,
                    "orderId", order.getOrderId());
            return;
        }
        order.markStatus(OrderStatus.FAILED, now);
        order.incrementVersion();
        OpenLog.warn(log, "OrderPositionReserveRejected", "Position reserve rejected", null,
                "orderId", order.getOrderId(),
                "reason", reason);
    }
}
