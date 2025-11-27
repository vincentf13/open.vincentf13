package open.vincentf13.exchange.order.infra.messaging.handler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.infra.persistence.po.OrderPO;
import open.vincentf13.exchange.order.infra.persistence.repository.OrderRepository;
import open.vincentf13.exchange.common.sdk.enums.OrderStatus;
import open.vincentf13.exchange.order.infra.OrderEvent;
import open.vincentf13.sdk.core.log.OpenLog;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OrderFailureHandler {

    private final OrderRepository orderRepository;
    private final TransactionTemplate transactionTemplate;

    public void markFailed(Long orderId, String stage, String reason) {
        if (orderId == null) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            Optional<Order> optional = orderRepository.findOne(Wrappers.<OrderPO>lambdaQuery()
                    .eq(OrderPO::getOrderId, orderId));
            if (optional.isEmpty()) {
                OpenLog.warn(OrderEvent.ORDER_FAILURE_SKIP_NOT_FOUND,
                        "stage", stage,
                        "orderId", orderId);
                return;
            }
            Order order = optional.get();
            Instant now = Instant.now();
            int currentVersion = Optional.ofNullable(order.getVersion()).orElse(0);
            Order updateRecord = Order.builder()
                    .status(OrderStatus.FAILED)
                    .version(currentVersion + 1)
                    .build();
            boolean updated = orderRepository.updateSelective(updateRecord,
                    Wrappers.<OrderPO>lambdaUpdate()
                            .eq(OrderPO::getOrderId, order.getOrderId())
                            .eq(OrderPO::getUserId, order.getUserId())
                            .eq(OrderPO::getVersion, currentVersion));
            if (!updated) {
                OpenLog.warn(OrderEvent.ORDER_FAILURE_OPTIMISTIC_LOCK,
                        "orderId", orderId,
                        "stage", stage);
                return;
            }
            order.markStatus(OrderStatus.FAILED, now);
            order.incrementVersion();
            OpenLog.warn(OrderEvent.ORDER_MARK_FAILED,
                    "orderId", orderId,
                    "stage", stage,
                    "reason", reason);
        });
    }
}
