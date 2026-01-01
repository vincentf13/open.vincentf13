package open.vincentf13.exchange.risk.sdk.rest.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import open.vincentf13.sdk.auth.auth.PublicAPI;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Risk Maintenance", description = "風控維護接口")
@RequestMapping("/api/risk/maintenance")
public interface RiskMaintenanceApi {

    @Operation(summary = "重新加載風控快取", description = "觸發 InstrumentCache 與 MarkPriceCache 的重新載入")
    @PostMapping("/reload-caches")
    @PublicAPI // 設為 Public 以便從 Admin 直接調用，或視需求改為 Private
    OpenApiResponse<Void> reloadCaches();
}
