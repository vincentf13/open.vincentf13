package open.vincentf13.exchange.order.controller;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.order.sdk.rest.api.OrderApi;
import open.vincentf13.exchange.order.sdk.rest.dto.OrderCreateRequest;
import open.vincentf13.exchange.order.sdk.rest.dto.OrderResponse;
import open.vincentf13.exchange.order.service.OrderCommandService;
import open.vincentf13.exchange.order.service.OrderQueryService;
import open.vincentf13.sdk.auth.jwt.OpenJwtLoginUserHolder;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController implements OrderApi {
    
    private final OrderCommandService orderCommandService;
    private final OrderQueryService orderQueryService;
    
    @Override
    public OpenApiResponse<OrderResponse> create(OrderCreateRequest request) {
        return OpenApiResponse.success(orderCommandService.createOrder(request));
    }
    
    @Override
    public OpenApiResponse<OrderResponse> getOrder(Long orderId) {
        return OpenApiResponse.success(orderQueryService.get(orderId));
    }

    @Override
    public OpenApiResponse<List<OrderResponse>> getOrders(Long userId,
                                                          Long instrumentId) {
        Long jwtUserId = OpenJwtLoginUserHolder.currentUserId();
        Long resolvedUserId = jwtUserId != null ? jwtUserId : userId;
        return OpenApiResponse.success(orderQueryService.getOrders(resolvedUserId, instrumentId));
    }
}
