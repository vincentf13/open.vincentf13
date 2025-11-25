package open.vincentf13.exchange.order.infra.persistence.repository;

import com.github.yitter.idgen.DefaultIdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.order.infra.persistence.mapper.OrderMapper;
import open.vincentf13.exchange.order.infra.persistence.po.OrderPO;
import open.vincentf13.exchange.order.sdk.rest.api.enums.OrderStatus;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import open.vincentf13.exchange.order.domain.model.Order;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OrderRepository {

    private final OrderMapper mapper;
    private final DefaultIdGenerator idGenerator;

    public void insertSelective(@NotNull @Valid Order order) {
        order.setOrderId(idGenerator.newLong());
        OrderPO po = OpenObjectMapper.convert(order, OrderPO.class);
        mapper.insertSelective(po);
    }

    public List<Order> findBy(@NotNull Order condition) {
        OrderPO probe = OpenObjectMapper.convert(condition, OrderPO.class);
        return mapper.findBy(probe).stream()
                .map(item -> OpenObjectMapper.convert(item, Order.class))
                .toList();
    }

    public Optional<Order> findOne(@NotNull Order condition) {
        OrderPO probe = OpenObjectMapper.convert(condition, OrderPO.class);
        var results = mapper.findBy(probe);
        if (results == null || results.isEmpty()) {
            return Optional.empty();
        }
        if (results.size() > 1) {
            throw new IllegalStateException("Expected single order but found " + results.size());
        }
        return Optional.of(OpenObjectMapper.convert(results.get(0), Order.class));
    }

    public boolean updateSelectiveBy(@NotNull @Valid Order update,
                                     @NotNull Long orderId,
                                     Long userId,
                                     Integer expectedVersion,
                                     OrderStatus currentStatus) {
        OrderPO record = OpenObjectMapper.convert(update, OrderPO.class);
        return mapper.updateSelectiveBy(record, orderId, userId, expectedVersion, currentStatus) > 0;
    }

}
