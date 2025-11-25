package open.vincentf13.exchange.order.service;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.infra.OrderErrorCodeEnum;
import open.vincentf13.exchange.order.infra.persistence.repository.OrderRepository;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderResponse;
import open.vincentf13.sdk.core.OpenMapstruct;
import open.vincentf13.sdk.core.exception.OpenServiceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public OrderResponse get(Long orderId) {
        Order order = orderRepository.findOne(Order.builder().orderId(orderId).build())
                .orElseThrow(() -> OpenServiceException.of(OrderErrorCodeEnum.ORDER_NOT_FOUND,
                                                           "Order not found: " + orderId));
        return OpenMapstruct.map(order, OrderResponse.class);
    }
}
