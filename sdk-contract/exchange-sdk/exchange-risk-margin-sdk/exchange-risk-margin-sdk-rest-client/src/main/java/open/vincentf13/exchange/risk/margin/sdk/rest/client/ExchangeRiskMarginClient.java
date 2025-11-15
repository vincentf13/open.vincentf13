package open.vincentf13.exchange.risk.margin.sdk.rest.client;

import open.vincentf13.exchange.risk.margin.sdk.rest.api.RiskMarginApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(
        contextId = "exchangeRiskMarginClient",
        name = "${exchange.risk.margin.client.name:exchange-risk}",
        url = "${exchange.risk.margin.client.url:}",
        path = "/api/risk"
)
public interface ExchangeRiskMarginClient extends RiskMarginApi {
}
