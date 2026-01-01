package open.vincentf13.exchange.risk.controller;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.risk.infra.bootstrap.StartupCacheLoader;
import open.vincentf13.exchange.risk.sdk.rest.api.RiskMaintenanceApi;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/risk/maintenance")
@RequiredArgsConstructor
public class RiskMaintenanceController implements RiskMaintenanceApi {

    private final StartupCacheLoader startupCacheLoader;

    @Override
    public OpenApiResponse<Void> reloadCaches() {
        // 調用現有的啟動加載邏輯來更新最新數據
        startupCacheLoader.loadCaches();
        return OpenApiResponse.success(null);
    }
}
