package open.vincentf13.exchange.account.sdk.rest.client;

import open.vincentf13.exchange.account.sdk.rest.api.AccountApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(
        contextId = "exchangeAccountClient",
        name = "${exchange.account.client.name:exchange-account}",
        url = "${exchange.account.client.url:}",
        path = "/api/account"
)
public interface ExchangeAccountClient extends AccountApi {
}
