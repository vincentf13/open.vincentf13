package open.vincentf13.exchange.admin.contract.client;

import open.vincentf13.exchange.admin.contract.InstrumentAdminApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(
        contextId = "exchangeAdminClient",
        name = "${exchange.admin.client.name:exchange-admin}",
        url = "${exchange.admin.client.url:}",
        path = "/api/admin/instruments"
)
public interface ExchangeAdminClient extends InstrumentAdminApi {
}
