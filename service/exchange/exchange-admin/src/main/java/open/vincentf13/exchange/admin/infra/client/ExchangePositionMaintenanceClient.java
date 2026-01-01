package open.vincentf13.exchange.admin.infra.client;

import open.vincentf13.exchange.position.sdk.rest.api.PositionMaintenanceApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "exchange-position", contextId = "exchangePositionMaintenanceClient")
public interface ExchangePositionMaintenanceClient extends PositionMaintenanceApi {
}