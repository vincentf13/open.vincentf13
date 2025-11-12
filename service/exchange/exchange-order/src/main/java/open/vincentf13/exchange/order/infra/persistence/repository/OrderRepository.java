package open.vincentf13.exchange.order.infra.persistence.repository;

import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderStatus;

import java.time.Instant;
import java.util.Optional;

public interface OrderRepository {

    void insert(Order order);

    Optional<Order> findById(Long orderId);

    Optional<Order> findByUserIdAndClientOrderId(Long userId, String clientOrderId);

    boolean updateStatus(Long orderId, Long userId, OrderStatus status, Instant updatedAt, int expectedVersion);
}
