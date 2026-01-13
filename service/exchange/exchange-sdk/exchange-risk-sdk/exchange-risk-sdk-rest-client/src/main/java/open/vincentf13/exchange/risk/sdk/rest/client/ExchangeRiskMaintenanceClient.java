package open.vincentf13.exchange.risk.sdk.rest.client;

import open.vincentf13.exchange.risk.sdk.rest.api.RiskMaintenanceApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(
    contextId = "exchangeRiskMaintenanceClient",
    name = "${exchange.risk.client.name:exchange-risk}",
    url = "${exchange.risk.client.url:}",
    path = "/api/risk/maintenance")
public interface ExchangeRiskMaintenanceClient extends RiskMaintenanceApi {}
