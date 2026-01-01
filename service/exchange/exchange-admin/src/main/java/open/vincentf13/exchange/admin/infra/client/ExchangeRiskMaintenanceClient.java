package open.vincentf13.exchange.admin.infra.client;

import open.vincentf13.exchange.risk.sdk.rest.api.RiskMaintenanceApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "exchange-risk", contextId = "exchangeRiskMaintenanceClient")
public interface ExchangeRiskMaintenanceClient extends RiskMaintenanceApi {
}
