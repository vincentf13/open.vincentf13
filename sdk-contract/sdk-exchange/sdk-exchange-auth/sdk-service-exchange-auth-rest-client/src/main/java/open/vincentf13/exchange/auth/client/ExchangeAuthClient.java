package open.vincentf13.exchange.auth.client;

import open.vincentf13.exchange.auth.api.AuthCredentialApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(
        contextId = "exchangeAuthClient",
        name = "${exchange.auth.client.name:exchange-auth}",
        url = "${exchange.auth.client.url:}"
)
public interface ExchangeAuthClient extends AuthCredentialApi {
}
