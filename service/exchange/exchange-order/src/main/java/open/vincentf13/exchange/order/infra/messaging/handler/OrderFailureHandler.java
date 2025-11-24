package open.vincentf13.exchange.order.infra.messaging.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.infra.persistence.repository.OrderRepository;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderFailureHandler {

    private final OrderRepository orderRepository;
    private final TransactionTemplate transactionTemplate;

    public void markFailed(Long orderId, String stage, String reason) {
        if (orderId == null) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            Optional<Order> optional = orderRepository.findOne(Order.builder().orderId(orderId).build());
            if (optional.isEmpty()) {
                log.warn("Skip {} failure event, order not found. orderId={}", stage, orderId);
                return;
            }
            Order order = optional.get();
            Instant now = Instant.now();
            int currentVersion = Optional.ofNullable(order.getVersion()).orElse(0);
            Order updateRecord = Order.builder()
                    .status(OrderStatus.FAILED)
                    .version(currentVersion + 1)
                    .build();
            boolean updated = orderRepository.updateSelectiveBy(updateRecord, order.getOrderId(), order.getUserId(), currentVersion);
            if (!updated) {
                log.warn("Optimistic lock conflict while marking order failed. orderId={} stage={}", orderId, stage);
                return;
            }
            order.markStatus(OrderStatus.FAILED, now);
            order.incrementVersion();
            log.warn("Order marked FAILED due to {}. orderId={} reason={}", stage, orderId, reason);
        });
    }
}
