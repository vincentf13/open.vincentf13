package open.vincentf13.exchange.order.sdk.rest.client;

import open.vincentf13.exchange.order.sdk.rest.api.OrderApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(
    contextId = "exchangeOrderClient",
    name = "${exchange.order.client.name:exchange-order}",
    url = "${exchange.order.client.url:}",
    path = "/api/orders")
public interface ExchangeOrderClient extends OrderApi {}
