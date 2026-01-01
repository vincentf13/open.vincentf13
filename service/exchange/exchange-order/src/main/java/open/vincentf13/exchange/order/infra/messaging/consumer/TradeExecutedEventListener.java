package open.vincentf13.exchange.order.infra.messaging.consumer;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.common.sdk.constants.ValidationConstant;
import open.vincentf13.exchange.common.sdk.enums.OrderStatus;
import open.vincentf13.exchange.matching.sdk.mq.event.TradeExecutedEvent;
import open.vincentf13.exchange.matching.sdk.mq.topic.MatchingTopics;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.infra.OrderEvent;
import open.vincentf13.exchange.order.infra.persistence.po.OrderPO;
import open.vincentf13.exchange.order.infra.persistence.repository.OrderRepository;
import open.vincentf13.exchange.order.infra.persistence.repository.OrderEventRepository;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderEventReferenceType;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderEventType;
import open.vincentf13.sdk.core.OpenValidator;
import open.vincentf13.sdk.core.log.OpenLog;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class TradeExecutedEventListener implements ConsumerSeekAware {

    private final OrderRepository orderRepository;
    private final OrderEventRepository orderEventRepository;
    private final TransactionTemplate transactionTemplate;
    private static final String ACTOR_MATCHING = "MATCHING_ENGINE";
    private static final Set<OrderStatus> UPDATABLE_STATUSES =
            EnumSet.of(OrderStatus.NEW,
                       OrderStatus.PARTIALLY_FILLED,
                       OrderStatus.FILLED,
                       OrderStatus.CANCELLING,
                       OrderStatus.CANCELLED);

    /**
     調試用
     * @param assignments
     * @param callback
     */
    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        callback.seekToBeginning(assignments.keySet());
    }

    @KafkaListener(topics = MatchingTopics.Names.TRADE_EXECUTED,
                   groupId = "${open.vincentf13.exchange.order.consumer-group:exchange-order}")
    public void onTradeExecuted(@Payload TradeExecutedEvent event,
                                Acknowledgment acknowledgment) {
        OpenValidator.validateOrThrow(event);
        applyFill(event.tradeId(),
                  event.orderId(),
                  event.price(),
                  event.quantity(),
                  event.makerFee(),
                  event.executedAt());
        applyFill(event.tradeId(),
                  event.counterpartyOrderId(),
                  event.price(),
                  event.quantity(),
                  event.takerFee(),
                  event.executedAt());
        acknowledgment.acknowledge();
    }

    private void applyFill(Long tradeId,
                           Long targetOrderId,
                           BigDecimal price,
                           BigDecimal filledQuantity,
                           BigDecimal feeDelta,
                           Instant executedAt) {
        transactionTemplate.executeWithoutResult(status -> {
            if (orderEventRepository.existsByReference(targetOrderId, OrderEventReferenceType.TRADE, tradeId)) {
                return;
            }
            Order order = orderRepository.findOne(
                            Wrappers.<OrderPO>lambdaQuery().eq(OrderPO::getOrderId, targetOrderId))
                    .orElse(null);
            if (order == null) {
                OpenLog.warn(OrderEvent.ORDER_NOT_FOUND_AFTER_RESERVE,
                             Map.of("orderId", targetOrderId,
                                    "tradeId", tradeId));
                return;
            }
            if (!UPDATABLE_STATUSES.contains(order.getStatus())) {
                return;
            }
            BigDecimal previousFilled = order.getFilledQuantity() == null ? BigDecimal.ZERO : order.getFilledQuantity();
            BigDecimal orderQuantity = order.getQuantity();
            BigDecimal newFilled = previousFilled.add(filledQuantity);
            if (newFilled.compareTo(orderQuantity) > 0) {
                newFilled = orderQuantity;
            }
            BigDecimal newRemaining = orderQuantity.subtract(newFilled);
            if (newRemaining.compareTo(BigDecimal.ZERO) < 0) {
                newRemaining = BigDecimal.ZERO;
            }
            BigDecimal totalValueBefore = order.getAvgFillPrice() == null
                                          ? BigDecimal.ZERO
                                          : order.getAvgFillPrice().multiply(previousFilled);
            BigDecimal totalValueAfter = totalValueBefore.add(price.multiply(filledQuantity));
            BigDecimal newAvgPrice = newFilled.compareTo(BigDecimal.ZERO) == 0
                                     ? order.getAvgFillPrice()
                                     : totalValueAfter.divide(newFilled, ValidationConstant.Names.COMMON_SCALE, RoundingMode.HALF_UP);
            order.setFilledQuantity(newFilled);
            order.setRemainingQuantity(newRemaining);
            order.setAvgFillPrice(newAvgPrice);
            order.setFee(order.getFee() == null ? feeDelta : order.getFee().add(feeDelta));
            boolean orderFilled = newRemaining.compareTo(BigDecimal.ZERO) == 0;
            order.setStatus(orderFilled ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED);
            if (orderFilled && order.getFilledAt() == null) {
                order.setFilledAt(executedAt);
            }
            int expectedVersion = order.getVersion() == null ? 0 : order.getVersion();
            order.setVersion(expectedVersion + 1);
            LambdaUpdateWrapper<OrderPO> updateWrapper = Wrappers.<OrderPO>lambdaUpdate()
                                                                 .eq(OrderPO::getOrderId, targetOrderId)
                                                                 .eq(OrderPO::getVersion, expectedVersion);
            boolean updated = orderRepository.updateSelective(order, updateWrapper);
            if (!updated) {
                OpenLog.warn(OrderEvent.ORDER_FAILURE_OPTIMISTIC_LOCK,
                             Map.of("orderId", targetOrderId,
                                    "tradeId", tradeId));
                status.setRollbackOnly();
                return;
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("tradeId", tradeId);
            payload.put("fillPrice", price);
            payload.put("fillQuantity", filledQuantity);
            payload.put("feeDelta", feeDelta);
            payload.put("filledQuantity", order.getFilledQuantity());
            payload.put("remainingQuantity", order.getRemainingQuantity());
            payload.put("status", order.getStatus().name());
            orderEventRepository.append(order,
                                        OrderEventType.ORDER_TRADE_FILLED,
                                        ACTOR_MATCHING,
                                        executedAt,
                                        payload,
                                        OrderEventReferenceType.TRADE,
                                        tradeId);
        });
    }
}
