package open.vincentf13.exchange.account.controller;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.account.infra.bootstrap.AccountStartupCacheLoader;
import open.vincentf13.exchange.account.infra.cache.InstrumentCache;
import open.vincentf13.exchange.account.sdk.rest.api.AccountMaintenanceApi;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account/maintenance")
@RequiredArgsConstructor
public class AccountMaintenanceController implements AccountMaintenanceApi {

    private final AccountStartupCacheLoader accountStartupCacheLoader;
    private final InstrumentCache instrumentCache;

    @Override
    public OpenApiResponse<Void> reloadCaches() {
        instrumentCache.clear();
        accountStartupCacheLoader.loadCaches();
        return OpenApiResponse.success(null);
    }
}
