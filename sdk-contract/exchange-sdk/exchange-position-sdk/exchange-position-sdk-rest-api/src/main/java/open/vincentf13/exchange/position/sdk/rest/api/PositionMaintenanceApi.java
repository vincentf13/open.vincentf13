package open.vincentf13.exchange.position.sdk.rest.api;

import open.vincentf13.sdk.auth.auth.PublicAPI;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/position/maintenance")
public interface PositionMaintenanceApi {

    @PostMapping("/reload-caches")
    @PublicAPI
    OpenApiResponse<Void> reloadCaches();
}
