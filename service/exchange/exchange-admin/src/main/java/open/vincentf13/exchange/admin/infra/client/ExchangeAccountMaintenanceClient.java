package open.vincentf13.exchange.admin.infra.client;

import open.vincentf13.exchange.account.sdk.rest.api.AccountMaintenanceApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "exchange-account", contextId = "exchangeAccountMaintenanceClient")
public interface ExchangeAccountMaintenanceClient extends AccountMaintenanceApi {
}
