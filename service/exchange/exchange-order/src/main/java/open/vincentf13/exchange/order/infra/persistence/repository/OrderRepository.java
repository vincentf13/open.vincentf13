package open.vincentf13.exchange.order.infra.persistence.repository;

import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface OrderRepository {

    void insert(Order order);

    Optional<Order> findById(Long orderId);

    Optional<Order> findByUserIdAndClientOrderId(Long userId, String clientOrderId);

    default boolean updateStatus(Long orderId, Long userId, OrderStatus status, Instant updatedAt, int expectedVersion) {
        return updateStatus(orderId, userId, status, updatedAt, expectedVersion, null, null);
    }

    boolean updateStatus(Long orderId, Long userId, OrderStatus status, Instant updatedAt,
                         int expectedVersion, Instant submittedAt, Instant filledAt);

    boolean updateStatusByCurrentStatus(Long orderId,
                                        Long userId,
                                        OrderStatus currentStatus,
                                        OrderStatus targetStatus,
                                        Instant updatedAt,
                                        Instant submittedAt,
                                        Instant filledAt);

    boolean updateStatusAndCost(Long orderId,
                                Long userId,
                                OrderStatus currentStatus,
                                OrderStatus targetStatus,
                                Instant updatedAt,
                                Instant submittedAt,
                                Instant filledAt,
                                BigDecimal closeCostPrice);

    boolean updateAfterTrade(Order order, Instant updatedAt, int expectedVersion);
}
