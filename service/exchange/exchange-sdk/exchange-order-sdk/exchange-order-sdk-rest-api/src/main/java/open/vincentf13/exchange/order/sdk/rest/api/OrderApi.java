package open.vincentf13.exchange.order.sdk.rest.api;

import jakarta.validation.Valid;
import java.util.List;
import open.vincentf13.exchange.order.sdk.rest.dto.OrderCreateRequest;
import open.vincentf13.exchange.order.sdk.rest.dto.OrderEventResponse;
import open.vincentf13.exchange.order.sdk.rest.dto.OrderResponse;
import open.vincentf13.sdk.auth.auth.Jwt;
import open.vincentf13.sdk.auth.auth.PrivateAPI;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Validated
public interface OrderApi {

  @PostMapping
  @Jwt
  OpenApiResponse<OrderResponse> create(@Valid @RequestBody OrderCreateRequest request);

  @GetMapping("/{orderId}")
  @Jwt
  @PrivateAPI
  OpenApiResponse<OrderResponse> getOrder(@PathVariable("orderId") Long orderId);

  @GetMapping("/{orderId}/events")
  @Jwt
  OpenApiResponse<OrderEventResponse> getOrderEvents(@PathVariable("orderId") Long orderId);

  @GetMapping
  @Jwt
  @PrivateAPI
  OpenApiResponse<List<OrderResponse>> getOrders(
      @RequestParam(value = "userId", required = false) Long userId,
      @RequestParam(value = "instrumentId", required = false) Long instrumentId);
}
