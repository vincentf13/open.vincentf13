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
import open.vincentf13.sdk.core.OpenValidator;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class TradeExecutedEventListener {

    private final OrderRepository orderRepository;
    private static final Set<OrderStatus> UPDATABLE_STATUSES =
            EnumSet.of(OrderStatus.NEW,
                       OrderStatus.PARTIALLY_FILLED,
                       OrderStatus.FILLED,
                       OrderStatus.CANCELLING,
                       OrderStatus.CANCELLED);

    @KafkaListener(topics = MatchingTopics.Names.TRADE_EXECUTED,
                   groupId = "${open.vincentf13.exchange.order.consumer-group:exchange-order}")
    public void onTradeExecuted(@Payload TradeExecutedEvent event,
                                Acknowledgment acknowledgment) {
        OpenValidator.validateOrThrow(event);
        applyFill(event.orderId(), event.price(), event.quantity(), event.makerFee(), event.executedAt());
        applyFill(event.counterpartyOrderId(), event.price(), event.quantity(), event.takerFee(), event.executedAt());
        acknowledgment.acknowledge();
    }

    private void applyFill(Long targetOrderId,
                           BigDecimal price,
                           BigDecimal filledQuantity,
                           BigDecimal feeDelta,
                           Instant executedAt) {
        Order order = orderRepository.findOne(
                        Wrappers.<OrderPO>lambdaQuery()
                                .eq(OrderPO::getOrderId, targetOrderId))
                .orElse(null);
        if (order == null) {
            OpenLog.warn(OrderEvent.ORDER_NOT_FOUND_AFTER_RESERVE, Map.of("orderId", targetOrderId));
            return;
        }
        if (!UPDATABLE_STATUSES.contains(order.getStatus())) {
            return;
        }
        BigDecimal previousFilled = order.getFilledQuantity();
        BigDecimal newFilled = previousFilled.add(filledQuantity);
        if (newFilled.compareTo(order.getQuantity()) > 0) {
            newFilled = order.getQuantity();
        }
        BigDecimal newRemaining = order.getQuantity().subtract(newFilled).max(BigDecimal.ZERO);
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
        order.setStatus(newRemaining.compareTo(BigDecimal.ZERO) == 0 ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED);
        order.setFilledAt(newRemaining.compareTo(BigDecimal.ZERO) == 0 ? executedAt : null);
        int expectedVersion = order.getVersion() == null ? 0 : order.getVersion();
        order.setVersion(expectedVersion + 1);
        LambdaUpdateWrapper<OrderPO> updateWrapper = Wrappers.<OrderPO>lambdaUpdate()
                                                             .eq(OrderPO::getOrderId, targetOrderId)
                                                             .eq(OrderPO::getVersion, expectedVersion);
        boolean updated = orderRepository.updateSelective(order, updateWrapper);
        if (!updated) {
            OpenLog.warn(OrderEvent.ORDER_FAILURE_OPTIMISTIC_LOCK, Map.of("orderId", targetOrderId));
        }
    }
}
