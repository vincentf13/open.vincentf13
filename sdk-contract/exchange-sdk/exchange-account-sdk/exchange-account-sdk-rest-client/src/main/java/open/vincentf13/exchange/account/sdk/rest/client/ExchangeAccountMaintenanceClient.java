package open.vincentf13.exchange.account.sdk.rest.client;

import open.vincentf13.exchange.account.sdk.rest.api.AccountMaintenanceApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(
        contextId = "exchangeAccountMaintenanceClient",
        name = "${exchange.account.client.name:exchange-account}",
        url = "${exchange.account.client.url:}"
)
public interface ExchangeAccountMaintenanceClient extends AccountMaintenanceApi {
}
