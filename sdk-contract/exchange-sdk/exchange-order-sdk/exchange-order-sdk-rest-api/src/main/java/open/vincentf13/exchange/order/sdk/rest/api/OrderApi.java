package open.vincentf13.exchange.order.sdk.rest.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderCreateRequest;
import open.vincentf13.exchange.order.sdk.rest.api.dto.OrderResponse;
import open.vincentf13.sdk.auth.auth.Jwt;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Validated
public interface OrderApi {

    @PostMapping
    @Jwt
    OpenApiResponse<OrderResponse> create(@Valid @RequestBody OrderCreateRequest request);

    @GetMapping("/{orderId}")
    @Jwt
    OpenApiResponse<OrderResponse> findById(@PathVariable("orderId") @NotNull Long orderId);

    @DeleteMapping("/{orderId}")
    @Jwt
    OpenApiResponse<Void> cancel(@PathVariable("orderId") @NotNull Long orderId);
}
