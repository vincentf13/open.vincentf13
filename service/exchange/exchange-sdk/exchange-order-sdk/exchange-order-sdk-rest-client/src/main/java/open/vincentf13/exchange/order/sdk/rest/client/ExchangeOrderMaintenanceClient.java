package open.vincentf13.exchange.order.sdk.rest.client;

import open.vincentf13.exchange.order.sdk.rest.api.OrderMaintenanceApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(
    name = "exchange-order",
    contextId = "ExchangeOrderMaintenanceClient",
    path = "/api/order/maintenance")
public interface ExchangeOrderMaintenanceClient extends OrderMaintenanceApi {}
