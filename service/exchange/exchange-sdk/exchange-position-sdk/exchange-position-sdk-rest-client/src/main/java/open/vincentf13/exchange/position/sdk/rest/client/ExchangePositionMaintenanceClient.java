package open.vincentf13.exchange.position.sdk.rest.client;

import open.vincentf13.exchange.position.sdk.rest.api.PositionMaintenanceApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(
        contextId = "exchangePositionMaintenanceClient",
        name = "${exchange.position.client.name:exchange-position}",
        url = "${exchange.position.client.url:}",
        path = "/api/position/maintenance"
)
public interface ExchangePositionMaintenanceClient extends PositionMaintenanceApi {
}
