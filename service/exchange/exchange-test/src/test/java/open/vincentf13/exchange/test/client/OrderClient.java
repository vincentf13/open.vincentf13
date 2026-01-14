package open.vincentf13.exchange.test.client;

import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.OrderType;
import open.vincentf13.exchange.order.sdk.rest.api.OrderApi;
import open.vincentf13.exchange.order.sdk.rest.dto.OrderCreateRequest;

import java.math.BigDecimal;
import java.util.UUID;

class OrderClient {
    private final String gatewayHost;

    OrderClient(String gatewayHost) {
        this.gatewayHost = gatewayHost;
    }

    void placeOrder(String token, int instrumentId, OrderSide side, double price, int quantity) {
        OrderApi orderApi = FeignClientSupport.buildClient(
            OrderApi.class, gatewayHost + "/order/api/orders", token);
        OrderCreateRequest request = OrderCreateRequest.builder()
            .instrumentId((long) instrumentId)
            .side(side)
            .type(OrderType.LIMIT)
            .price(BigDecimal.valueOf(price))
            .quantity(BigDecimal.valueOf(quantity))
            .clientOrderId(UUID.randomUUID().toString())
            .build();
        FeignClientSupport.assertSuccess(orderApi.create(request), "order.create");
    }
}
