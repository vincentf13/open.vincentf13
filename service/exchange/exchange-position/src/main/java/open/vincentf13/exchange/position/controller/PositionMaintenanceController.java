package open.vincentf13.exchange.position.controller;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.position.infra.bootstrap.StartupCacheLoader;
import open.vincentf13.exchange.position.sdk.rest.api.PositionMaintenanceApi;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PositionMaintenanceController implements PositionMaintenanceApi {

    private final StartupCacheLoader startupCacheLoader;

    @Override
    public OpenApiResponse<Void> reloadCaches() {
        startupCacheLoader.loadCaches();
        return OpenApiResponse.success(null);
    }
}
