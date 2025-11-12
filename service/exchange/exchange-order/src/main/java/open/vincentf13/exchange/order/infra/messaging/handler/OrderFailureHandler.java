package open.vincentf13.exchange.order.infra.messaging.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.infra.persistence.repository.OrderRepository;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderStatus;
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
            Optional<Order> optional = orderRepository.findById(orderId);
            if (optional.isEmpty()) {
                log.warn("Skip {} failure event, order not found. orderId={}", stage, orderId);
                return;
            }
            Order order = optional.get();
            Instant now = Instant.now();
            boolean updated = orderRepository.updateStatus(order.getOrderId(), order.getUserId(), OrderStatus.FAILED,
                    now, Optional.ofNullable(order.getVersion()).orElse(0));
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
