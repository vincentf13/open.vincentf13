package open.vincentf13.exchange.admin.infra.client;

import open.vincentf13.exchange.matching.sdk.rest.api.MatchingMaintenanceApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "exchange-matching", contextId = "exchangeMatchingMaintenanceClient")
public interface ExchangeMatchingMaintenanceClient extends MatchingMaintenanceApi {
}
