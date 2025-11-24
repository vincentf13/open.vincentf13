package open.vincentf13.exchange.order.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.infra.persistence.mapper.OrderMapper;
import open.vincentf13.exchange.order.infra.persistence.po.OrderPO;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderRepository {

    private final OrderMapper mapper;
    private final DefaultIdGenerator idGenerator;

    public void insert(@NotNull @Valid Order order) {
        order.setOrderId(idGenerator.newLong());
        OrderPO po = OpenMapstruct.map(order, OrderPO.class);
        mapper.insertSelective(po);
    }

    public Optional<Order> findById(@NotNull Long orderId) {
        OrderPO probe = OrderPO.builder().orderId(orderId).build();
        return Optional.ofNullable(mapper.findBy(probe))
                .map(po -> OpenMapstruct.map(po, Order.class));
    }

    public Optional<Order> findByUserIdAndClientOrderId(@NotNull Long userId, String clientOrderId) {
        if (userId == null || !StringUtils.hasText(clientOrderId)) {
            return Optional.empty();
        }
        OrderPO probe = OrderPO.builder()
                .userId(userId)
                .clientOrderId(clientOrderId)
                .build();
        return Optional.ofNullable(mapper.findBy(probe))
                .map(po -> OpenMapstruct.map(po, Order.class));
    }

    public boolean updateStatus(Long orderId, Long userId, OrderStatus status,
                                Instant updatedAt, int expectedVersion, Instant submittedAt, Instant filledAt) {
        OrderPO record = OrderPO.builder()
                .orderId(orderId)
                .userId(userId)
                .status(status)
                .updatedAt(updatedAt)
                .submittedAt(submittedAt)
                .filledAt(filledAt)
                .version(expectedVersion + 1)
                .expectedVersion(expectedVersion)
                .build();
        return mapper.updateSelective(record) > 0;
    }

    public boolean updateStatusByCurrentStatus(Long orderId,
                                               Long userId,
                                               OrderStatus currentStatus,
                                               OrderStatus targetStatus,
                                               Instant updatedAt,
                                               Instant submittedAt,
                                               Instant filledAt) {
        return mapper.updateStatusByCurrentStatus(orderId, userId, currentStatus, targetStatus, updatedAt, submittedAt, filledAt) > 0;
    }

    public boolean updateStatusAndCost(Long orderId,
                                       Long userId,
                                       OrderStatus currentStatus,
                                       OrderStatus targetStatus,
                                       Instant updatedAt,
                                       Instant submittedAt,
                                       Instant filledAt,
                                       BigDecimal closeCostPrice) {
        return mapper.updateStatusWithCost(orderId, userId, currentStatus, targetStatus, updatedAt, submittedAt, filledAt, closeCostPrice) > 0;
    }
}
