package open.vincentf13.exchange.risk.infra.client;

import open.vincentf13.exchange.market.sdk.rest.api.MarketApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(
        contextId = "exchangeMarketClient",
        name = "${exchange.market.client.name:exchange-market}",
        url = "${exchange.market.client.url:}",
        path = "/api/market"
)
public interface ExchangeMarketClient extends MarketApi {
}
