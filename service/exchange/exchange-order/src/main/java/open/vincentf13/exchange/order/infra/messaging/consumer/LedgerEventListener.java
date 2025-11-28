package open.vincentf13.exchange.order.infra.messaging.consumer;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.ledger.sdk.mq.event.FundsFreezeFailedEvent;
import open.vincentf13.exchange.account.ledger.sdk.mq.event.FundsFrozenEvent;
import open.vincentf13.exchange.account.ledger.sdk.mq.topic.LedgerTopics;
import open.vincentf13.exchange.common.sdk.enums.OrderStatus;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.infra.OrderEvent;
import open.vincentf13.exchange.order.infra.messaging.handler.OrderFailureHandler;
import open.vincentf13.exchange.order.infra.messaging.publisher.OrderEventPublisher;
import open.vincentf13.exchange.order.infra.persistence.po.OrderPO;
import open.vincentf13.exchange.order.infra.persistence.repository.OrderRepository;
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
public class LedgerEventListener {
    
    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final OrderFailureHandler orderFailureHandler;
    
    @KafkaListener(
            topics = LedgerTopics.Names.FUNDS_FROZEN,
            groupId = "${open.vincentf13.exchange.order.ledger.consumer-group:exchange-order-ledger}"
    )
    public void onFundsFrozen(@Payload FundsFrozenEvent event,
                              Acknowledgment acknowledgment) {
        try {
            OpenValidator.validateOrThrow(event);
        } catch (Exception e) {
            OpenLog.warn(OrderEvent.ORDER_LEDGER_PAYLOAD_INVALID, e, "event", event);
            acknowledgment.acknowledge();
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            Optional<Order> optional = orderRepository.findOne(Wrappers.<OrderPO>lambdaQuery()
                                                                       .eq(OrderPO::getOrderId, event.orderId()));
            if (optional.isEmpty()) {
                OpenLog.warn(OrderEvent.ORDER_FUNDS_FROZEN_SKIP_NOT_FOUND,
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
    public void onFundsFreezeFailed(@Payload FundsFreezeFailedEvent event,
                                    Acknowledgment acknowledgment) {
        try {
            OpenValidator.validateOrThrow(event);
        } catch (Exception e) {
            OpenLog.warn(OrderEvent.ORDER_LEDGER_PAYLOAD_INVALID, e, "event", event);
            acknowledgment.acknowledge();
            return;
        }
        orderFailureHandler.markFailed(event.orderId(), "LEDGER_REJECT", event.reason());
        acknowledgment.acknowledge();
    }
    
    private void handleFundsFrozen(Order order,
                                   FundsFrozenEvent event) {
        Instant now = Instant.now();
        int currentVersion = Optional.ofNullable(order.getVersion()).orElse(0);
        Order updateRecord = Order.builder()
                                  .status(OrderStatus.ACCEPTED)
                                  .submittedAt(now)
                                  .version(currentVersion + 1)
                                  .build();
        boolean updated = orderRepository.updateSelective(updateRecord,
                                                          Wrappers.<OrderPO>lambdaUpdate()
                                                                  .eq(OrderPO::getOrderId, order.getOrderId())
                                                                  .eq(OrderPO::getUserId, order.getUserId())
                                                                  .eq(OrderPO::getVersion, currentVersion));
        if (!updated) {
            OpenLog.warn(OrderEvent.ORDER_FUNDS_FROZEN_LOCK_CONFLICT,
                         "orderId", order.getOrderId());
            return;
        }
        order.markStatus(OrderStatus.ACCEPTED, now);
        order.setSubmittedAt(now);
        order.incrementVersion();
        orderEventPublisher.publishOrderCreated(order, event.asset(), event.frozenAmount());
        OpenLog.info(OrderEvent.ORDER_MARK_ACCEPTED,
                     "orderId", order.getOrderId(),
                     "asset", event.asset(),
                     "amount", event.frozenAmount());
    }
    
}
