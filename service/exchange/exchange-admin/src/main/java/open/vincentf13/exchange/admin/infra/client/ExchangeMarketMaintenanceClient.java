package open.vincentf13.exchange.admin.infra.client;

import open.vincentf13.exchange.market.sdk.rest.api.MarketMaintenanceApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "exchange-market", contextId = "exchangeMarketMaintenanceClient")
public interface ExchangeMarketMaintenanceClient extends MarketMaintenanceApi {
}
