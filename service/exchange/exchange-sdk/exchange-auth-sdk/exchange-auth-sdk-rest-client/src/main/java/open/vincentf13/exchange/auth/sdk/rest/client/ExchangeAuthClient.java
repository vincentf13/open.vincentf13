package open.vincentf13.exchange.auth.sdk.rest.client;

import open.vincentf13.exchange.auth.sdk.rest.api.AuthCredentialApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(
    contextId = "exchangeAuthClient",
    name = "${exchange.auth.client.name:exchange-auth}",
    url = "${exchange.auth.client.url:}",
    path = "/api/auth-credential")
public interface ExchangeAuthClient extends AuthCredentialApi {}
