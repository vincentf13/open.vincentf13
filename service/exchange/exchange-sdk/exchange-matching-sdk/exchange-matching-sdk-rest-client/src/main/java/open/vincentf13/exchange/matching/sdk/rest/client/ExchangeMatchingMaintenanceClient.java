package open.vincentf13.exchange.matching.sdk.rest.client;

import open.vincentf13.exchange.matching.sdk.rest.api.MatchingMaintenanceApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(
        contextId = "exchangeMatchingMaintenanceClient",
        name = "${exchange.matching.client.name:exchange-matching}",
        url = "${exchange.matching.client.url:}",
        path = "/api/matching/maintenance"
)
public interface ExchangeMatchingMaintenanceClient extends MatchingMaintenanceApi {
}
