package open.vincentf13.exchange.order.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.infra.persistence.mapper.OrderMapper;
import open.vincentf13.exchange.order.infra.persistence.po.OrderPO;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderStatus;
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
        order.setId(idGenerator.newLong());
        OrderPO po = OpenMapstruct.map(order, OrderPO.class);
        mapper.insert(po);
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
        return Optional.ofNullable(mapper.findById(orderId))
                .map(po -> OpenMapstruct.map(po, Order.class));
    }

    @Override
    public Optional<Order> findByUserIdAndClientOrderId(Long userId, String clientOrderId) {
        if (userId == null || !StringUtils.hasText(clientOrderId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.findByUserAndClientOrderId(userId, clientOrderId))
                .map(po -> OpenMapstruct.map(po, Order.class));
    }

    @Override
    public boolean updateStatus(Long orderId, Long userId, OrderStatus status,
                                Instant updatedAt, int expectedVersion) {
        return mapper.updateStatus(orderId, userId, status, updatedAt, expectedVersion) > 0;
    }
}
