package open.vincentf13.exchange.risk.sdk.rest.client;

import open.vincentf13.exchange.risk.sdk.rest.api.RiskApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(
    contextId = "exchangeRiskClient",
    name = "${exchange.risk.client.name:exchange-risk}",
    url = "${exchange.risk.client.url:}",
    path = "/api/risk")
public interface ExchangeRiskClient extends RiskApi {}
