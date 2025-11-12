package open.vincentf13.exchange.order.controller;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.order.sdk.rest.api.OrderApi;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderCreateRequest;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderResponse;
import open.vincentf13.exchange.order.service.OrderApplicationService;
import open.vincentf13.exchange.order.service.OrderQueryService;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController implements OrderApi {

    private final OrderApplicationService orderApplicationService;
    private final OrderQueryService orderQueryService;

    @Override
    public OpenApiResponse<OrderResponse> create(OrderCreateRequest request) {
        return OpenApiResponse.success(orderApplicationService.createOrder(request));
    }

    @Override
    public OpenApiResponse<OrderResponse> findById(Long orderId) {
        return OpenApiResponse.success(orderQueryService.getOrder(orderId));
    }

    @Override
    public OpenApiResponse<Void> cancel(Long orderId) {
        orderApplicationService.requestCancel(orderId);
        return OpenApiResponse.success(null);
    }
}
