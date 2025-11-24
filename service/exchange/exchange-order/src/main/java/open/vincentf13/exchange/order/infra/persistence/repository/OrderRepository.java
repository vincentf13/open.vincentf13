package open.vincentf13.exchange.order.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.infra.persistence.mapper.OrderMapper;
import open.vincentf13.exchange.order.infra.persistence.po.OrderPO;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderStatus;
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

    public void insertSelective(@NotNull @Valid Order order) {
        order.setOrderId(idGenerator.newLong());
        OrderPO po = OpenMapstruct.map(order, OrderPO.class);
        mapper.insertSelective(po);
    }

    public Optional<Order> findById(@NotNull Long orderId) {
        OrderPO probe = OrderPO.builder().orderId(orderId).build();
        return findOne(probe);
    }

    public Optional<Order> findByUserIdAndClientOrderId(@NotNull Long userId, String clientOrderId) {
        if (userId == null || !StringUtils.hasText(clientOrderId)) {
            return Optional.empty();
        }
        OrderPO probe = OrderPO.builder()
                .userId(userId)
                .clientOrderId(clientOrderId)
                .build();
        return findOne(probe);
    }

    private Optional<Order> findOne(@NotNull OrderPO condition) {
        var results = mapper.findBy(condition);
        if (results == null || results.isEmpty()) {
            return Optional.empty();
        }
        if (results.size() > 1) {
            throw new IllegalStateException("Expected single order but found " + results.size());
        }
        return Optional.of(OpenMapstruct.map(results.get(0), Order.class));
    }

    public boolean updateStatus(Long orderId,
                                Long userId,
                                OrderStatus status,
                                int expectedVersion,
                                Instant submittedAt,
                                Instant filledAt) {
        return mapper.updateStatusByIdAndVersion(orderId, userId, status, submittedAt, filledAt, expectedVersion) > 0;
    }

    public boolean updateStatusByCurrentStatus(Long orderId,
                                               Long userId,
                                               OrderStatus currentStatus,
                                               OrderStatus targetStatus,
                                               Instant submittedAt,
                                               Instant filledAt) {
        return mapper.updateStatusByCurrentStatus(orderId, userId, currentStatus, targetStatus, submittedAt, filledAt) > 0;
    }

    public boolean updateStatusAndCost(Long orderId,
                                       Long userId,
                                       OrderStatus currentStatus,
                                       OrderStatus targetStatus,
                                       Instant submittedAt,
                                       Instant filledAt,
                                       BigDecimal closeCostPrice) {
        return mapper.updateStatusWithCost(orderId, userId, currentStatus, targetStatus, submittedAt, filledAt, closeCostPrice) > 0;
    }
}
