package open.vincentf13.exchange.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.infra.OrderErrorCode;
import open.vincentf13.exchange.order.infra.persistence.po.OrderPO;
import open.vincentf13.exchange.order.infra.persistence.repository.OrderRepository;
import open.vincentf13.exchange.order.sdk.rest.dto.OrderResponse;
import open.vincentf13.sdk.core.exception.OpenException;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OrderQueryService {
    
    private final OrderRepository orderRepository;
    
    @Transactional(readOnly = true)
    public OrderResponse get(Long orderId) {
        Order order = orderRepository.findOne(Wrappers.<OrderPO>lambdaQuery()
                                                      .eq(OrderPO::getOrderId, orderId))
                                     .orElseThrow(() -> OpenException.of(OrderErrorCode.ORDER_NOT_FOUND,
                                                                         Map.of("orderId", orderId)));
        return OpenObjectMapper.convert(order, OrderResponse.class);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders(Long userId,
                                         Long instrumentId) {
        if (userId == null) {
            return List.of();
        }
        List<Order> orders = orderRepository.findBy(
                Wrappers.lambdaQuery(OrderPO.class)
                        .eq(OrderPO::getUserId, userId));
        if (orders.isEmpty()) {
            return List.of();
        }
        if (instrumentId != null) {
            List<Order> sorted = new ArrayList<>(orders);
            Comparator<Order> comparator = Comparator
                    .comparing((Order order) -> instrumentId.equals(order.getInstrumentId()) ? 0 : 1)
                    .thenComparing(Order::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
            sorted.sort(comparator);
            return OpenObjectMapper.convertList(sorted, OrderResponse.class);
        }
        return OpenObjectMapper.convertList(orders, OrderResponse.class);
    }
}
