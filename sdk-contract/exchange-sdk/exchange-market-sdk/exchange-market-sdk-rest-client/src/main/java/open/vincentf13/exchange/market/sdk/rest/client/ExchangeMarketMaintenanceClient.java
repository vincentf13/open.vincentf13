package open.vincentf13.exchange.market.sdk.rest.client;

import open.vincentf13.exchange.market.sdk.rest.api.MarketMaintenanceApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(
        contextId = "exchangeMarketMaintenanceClient",
        name = "${exchange.market.client.name:exchange-market}",
        url = "${exchange.market.client.url:}",
        path = "/api/market/maintenance"
)
public interface ExchangeMarketMaintenanceClient extends MarketMaintenanceApi {
}
