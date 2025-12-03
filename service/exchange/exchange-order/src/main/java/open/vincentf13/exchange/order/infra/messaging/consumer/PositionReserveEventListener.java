package open.vincentf13.exchange.order.infra.messaging.consumer;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.common.sdk.enums.OrderStatus;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.infra.OrderEvent;
import open.vincentf13.exchange.order.infra.messaging.publisher.OrderEventPublisher;
import open.vincentf13.exchange.order.infra.persistence.po.OrderPO;
import open.vincentf13.exchange.order.infra.persistence.repository.OrderRepository;
import open.vincentf13.exchange.position.sdk.mq.event.PositionReserveRejectedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionReservedEvent;
import open.vincentf13.exchange.position.sdk.mq.event.PositionTopics;
import open.vincentf13.sdk.core.OpenValidator;
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
public class PositionReserveEventListener {
    
    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final TransactionTemplate transactionTemplate;
    
    @KafkaListener(
            topics = PositionTopics.POSITION_RESERVED,
            groupId = "${exchange.order.position.consumer-group:exchange-order-position}"
    )
    public void onPositionReserved(@Payload PositionReservedEvent event,
                                   Acknowledgment acknowledgment) {
        try {
            OpenValidator.validateOrThrow(event);
        } catch (Exception e) {
            OpenLog.warn(OrderEvent.ORDER_POSITION_PAYLOAD_INVALID, e, "event", event);
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
    public void onPositionReserveRejected(@Payload PositionReserveRejectedEvent event,
                                          Acknowledgment acknowledgment) {
        try {
            OpenValidator.validateOrThrow(event);
        } catch (Exception e) {
            OpenLog.warn(OrderEvent.ORDER_POSITION_PAYLOAD_INVALID, e, "event", event);
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
                                  .closingEntryPrice(event.avgOpenPrice())
                                  .build();
        boolean updated = orderRepository.updateSelective(updateRecord,
                                                          Wrappers.<OrderPO>lambdaUpdate()
                                                                  .eq(OrderPO::getOrderId, event.orderId())
                                                                  .eq(OrderPO::getUserId, event.userId())
                                                                  .eq(OrderPO::getStatus, OrderStatus.PENDING));
        if (!updated) {
            OpenLog.warn(OrderEvent.ORDER_STATUS_CONFLICT, null,
                         "orderId", event.orderId());
            return;
        }
        Optional<Order> optionalOrder = orderRepository.findOne(Wrappers.<OrderPO>lambdaQuery()
                                                                        .eq(OrderPO::getOrderId, event.orderId()));
        if (optionalOrder.isEmpty()) {
            OpenLog.warn(OrderEvent.ORDER_NOT_FOUND_AFTER_RESERVE, null,
                         "orderId", event.orderId());
            return;
        }
        Order order = optionalOrder.get();
        order.markStatus(OrderStatus.SUBMITTED, now);
        order.setSubmittedAt(now);
        order.setClosingEntryPrice(event.avgOpenPrice());
        orderEventPublisher.publishOrderSubmitted(order);
        OpenLog.info(OrderEvent.ORDER_POSITION_RESERVED, "orderId", order.getOrderId());
    }
    
    private void handleReservationFailure(PositionReserveRejectedEvent event) {
        Instant now = Instant.now();
        Order updateRecord = Order.builder()
                                  .status(OrderStatus.FAILED)
                                  .build();
        boolean updated = orderRepository.updateSelective(updateRecord,
                                                          Wrappers.<OrderPO>lambdaUpdate()
                                                                  .eq(OrderPO::getOrderId, event.orderId())
                                                                  .eq(OrderPO::getUserId, event.userId())
                                                                  .eq(OrderPO::getStatus, OrderStatus.PENDING));
        if (!updated) {
            OpenLog.warn(OrderEvent.ORDER_STATUS_CONFLICT, null,
                         "orderId", event.orderId());
            return;
        }
        Optional<Order> optionalOrder = orderRepository.findOne(Wrappers.<OrderPO>lambdaQuery()
                                                                        .eq(OrderPO::getOrderId, event.orderId()));
        if (optionalOrder.isPresent()) {
            Order order = optionalOrder.get();
            order.markStatus(OrderStatus.FAILED, now);
            OpenLog.warn(OrderEvent.ORDER_POSITION_RESERVE_REJECTED, null,
                         "orderId", order.getOrderId(),
                         "reason", event.reason());
            return;
        }
        OpenLog.warn(OrderEvent.ORDER_NOT_FOUND_AFTER_RESERVE_REJECT, null,
                     "orderId", event.orderId(),
                     "reason", event.reason());
    }
}
