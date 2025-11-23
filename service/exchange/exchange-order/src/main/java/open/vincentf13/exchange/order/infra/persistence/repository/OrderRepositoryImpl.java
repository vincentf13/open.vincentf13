package open.vincentf13.exchange.order.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.infra.persistence.mapper.OrderMapper;
import open.vincentf13.exchange.order.infra.persistence.po.OrderPO;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderStatus;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepository {

    private final OrderMapper mapper;
    private final DefaultIdGenerator idGenerator;

    @Override
    public void insert(Order order) {
        order.setOrderId(idGenerator.newLong());
        OrderPO po = OpenMapstruct.map(order, OrderPO.class);
        mapper.insertSelective(po);
        if (po.getCreatedAt() != null) {
            order.setCreatedAt(po.getCreatedAt());
        }
        if (po.getUpdatedAt() != null) {
            order.setUpdatedAt(po.getUpdatedAt());
        }
    }

    @Override
    public Optional<Order> findById(Long orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        OrderPO probe = OrderPO.builder().orderId(orderId).build();
        return Optional.ofNullable(mapper.findBy(probe))
                .map(po -> OpenMapstruct.map(po, Order.class));
    }

    @Override
    public Optional<Order> findByUserIdAndClientOrderId(Long userId, String clientOrderId) {
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

    @Override
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

    @Override
    public boolean updateStatusByCurrentStatus(Long orderId,
                                               Long userId,
                                               OrderStatus currentStatus,
                                               OrderStatus targetStatus,
                                               Instant updatedAt,
                                               Instant submittedAt,
                                               Instant filledAt) {
        return mapper.updateStatusByCurrentStatus(orderId, userId, currentStatus, targetStatus, updatedAt, submittedAt, filledAt) > 0;
    }

    @Override
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

    @Override
    public boolean updateAfterTrade(Order order, Instant updatedAt, int expectedVersion) {
        OrderPO record = OrderPO.builder()
                .orderId(order.getOrderId())
                .userId(order.getUserId())
                .status(order.getStatus())
                .intent(order.getIntent())
                .closeCostPrice(order.getCloseCostPrice())
                .filledQuantity(order.getFilledQuantity())
                .remainingQuantity(order.getRemainingQuantity())
                .avgFillPrice(order.getAvgFillPrice())
                .fee(order.getFee())
                .filledAt(order.getFilledAt())
                .updatedAt(updatedAt)
                .version(expectedVersion + 1)
                .expectedVersion(expectedVersion)
                .build();
        return mapper.updateSelective(record) > 0;
    }
}
