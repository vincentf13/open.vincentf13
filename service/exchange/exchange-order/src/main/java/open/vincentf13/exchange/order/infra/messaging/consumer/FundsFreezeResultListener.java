package open.vincentf13.exchange.order.infra.messaging.consumer;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.sdk.mq.event.FundsFreezeFailedEvent;
import open.vincentf13.exchange.account.sdk.mq.event.FundsFrozenEvent;
import open.vincentf13.exchange.account.sdk.mq.topic.AccountFundsTopics;
import open.vincentf13.exchange.common.sdk.enums.OrderStatus;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.infra.OrderEvent;
import open.vincentf13.exchange.order.infra.messaging.publisher.OrderEventPublisher;
import open.vincentf13.exchange.order.infra.persistence.po.OrderPO;
import open.vincentf13.exchange.order.infra.persistence.repository.OrderRepository;
import open.vincentf13.exchange.order.mq.event.OrderCreatedEvent;
import open.vincentf13.sdk.core.OpenValidator;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class FundsFreezeResultListener {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;

    @KafkaListener(topics = AccountFundsTopics.Names.FUNDS_FROZEN,
                   groupId = "${open.vincentf13.exchange.order.consumer-group:exchange-order}")
    public void onFundsFrozen(@Payload FundsFrozenEvent event,
                              Acknowledgment acknowledgment) {
        OpenValidator.validateOrThrow(event);
        processFundsFrozen(event);
        acknowledgment.acknowledge();
    }

    @KafkaListener(topics = AccountFundsTopics.Names.FUNDS_FREEZE_FAILED,
                   groupId = "${open.vincentf13.exchange.order.consumer-group:exchange-order}")
    public void onFundsFreezeFailed(@Payload FundsFreezeFailedEvent event,
                                    Acknowledgment acknowledgment) {
        OpenValidator.validateOrThrow(event);
        processFundsFreezeFailed(event);
        acknowledgment.acknowledge();
    }

    private void processFundsFrozen(FundsFrozenEvent event) {
        Order order = orderRepository.findOne(
                Wrappers.<OrderPO>lambdaQuery().eq(OrderPO::getOrderId, event.orderId()))
                .orElse(null);
        if (order == null) {
            OpenLog.warn(OrderEvent.ORDER_NOT_FOUND_AFTER_RESERVE,
                         Map.of("orderId", event.orderId()));
            return;
        }
        if (order.getStatus() != OrderStatus.FREEZING_MARGIN) {
            return;
        }
        int expectedVersion = order.getVersion() == null ? 0 : order.getVersion();
        order.setStatus(OrderStatus.NEW);
        order.setSubmittedAt(event.eventTime());
        order.setVersion(expectedVersion + 1);
        boolean updated = orderRepository.updateSelective(
                order,
                Wrappers.<OrderPO>lambdaUpdate()
                        .eq(OrderPO::getOrderId, event.orderId())
                        .eq(OrderPO::getStatus, OrderStatus.FREEZING_MARGIN)
                        .eq(OrderPO::getVersion, expectedVersion));
        if (!updated) {
            OpenLog.warn(OrderEvent.ORDER_FAILURE_OPTIMISTIC_LOCK,
                         Map.of("orderId", event.orderId()));
            return;
        }
        orderEventPublisher.publishOrderCreated(new OrderCreatedEvent(
                order.getOrderId(),
                order.getUserId(),
                order.getInstrumentId(),
                order.getSide(),
                order.getType(),
                order.getIntent(),
                order.getPrice(),
                order.getQuantity(),
                order.getClientOrderId(),
                order.getSubmittedAt()
        ));
    }

    private void processFundsFreezeFailed(FundsFreezeFailedEvent event) {
        Order order = orderRepository.findOne(
                Wrappers.<OrderPO>lambdaQuery().eq(OrderPO::getOrderId, event.orderId()))
                .orElse(null);
        if (order == null) {
            OpenLog.warn(OrderEvent.ORDER_NOT_FOUND_AFTER_RESERVE,
                         Map.of("orderId", event.orderId()));
            return;
        }
        if (order.getStatus() != OrderStatus.FREEZING_MARGIN) {
            return;
        }
        int expectedVersion = order.getVersion() == null ? 0 : order.getVersion();
        order.setStatus(OrderStatus.REJECTED);
        order.setRejectedReason(event.reason() != null ? event.reason() : "FundsFreezeFailed");
        order.setVersion(expectedVersion + 1);
        boolean updated = orderRepository.updateSelective(
                order,
                Wrappers.<OrderPO>lambdaUpdate()
                        .eq(OrderPO::getOrderId, event.orderId())
                        .eq(OrderPO::getStatus, OrderStatus.FREEZING_MARGIN)
                        .eq(OrderPO::getVersion, expectedVersion));
        if (!updated) {
            OpenLog.warn(OrderEvent.ORDER_FAILURE_OPTIMISTIC_LOCK,
                         Map.of("orderId", event.orderId()));
        }
    }
}
