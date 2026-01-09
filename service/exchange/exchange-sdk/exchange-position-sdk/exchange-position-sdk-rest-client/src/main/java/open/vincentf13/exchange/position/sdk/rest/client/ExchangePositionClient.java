package open.vincentf13.exchange.position.sdk.rest.client;

import open.vincentf13.exchange.position.sdk.rest.api.PositionApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(
        contextId = "exchangePositionClient",
        name = "${exchange.position.client.name:exchange-position}",
        url = "${exchange.position.client.url:}",
        path = "/api/positions"
)
public interface ExchangePositionClient extends PositionApi {
}
