package open.vincentf13.exchange.account.sdk.rest.api;

import open.vincentf13.sdk.auth.auth.PublicAPI;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/account/maintenance")
public interface AccountMaintenanceApi {

    @PostMapping("/reload-caches")
    @PublicAPI
    OpenApiResponse<Void> reloadCaches();
}
