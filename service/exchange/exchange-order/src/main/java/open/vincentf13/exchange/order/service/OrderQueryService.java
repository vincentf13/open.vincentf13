package open.vincentf13.exchange.order.service;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.order.domain.model.Order;
import open.vincentf13.exchange.order.domain.model.OrderErrorCode;
import open.vincentf13.exchange.order.infra.persistence.repository.OrderRepository;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderResponse;
import open.vincentf13.sdk.auth.jwt.OpenJwtLoginUserInfo;
import open.vincentf13.sdk.core.exception.OpenServiceException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private final OrderRepository orderRepository;
    private final OrderAssembler orderAssembler;

    public OrderResponse getOrder(Long orderId) {
        Long userId = OpenJwtLoginUserInfo.currentUserIdOrThrow(() ->
                OpenServiceException.of(OrderErrorCode.ORDER_NOT_FOUND, "No authenticated user"));
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> OpenServiceException.of(OrderErrorCode.ORDER_NOT_FOUND,
                        "Order not found. id=" + orderId));
        if (!userId.equals(order.getUserId())) {
            throw OpenServiceException.of(OrderErrorCode.ORDER_NOT_OWNED,
                    "Order does not belong to current user. id=" + orderId);
        }
        return orderAssembler.toResponse(order);
    }
}
