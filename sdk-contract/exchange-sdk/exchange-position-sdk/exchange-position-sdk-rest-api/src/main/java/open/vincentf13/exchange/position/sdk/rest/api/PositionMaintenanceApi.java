package open.vincentf13.exchange.position.sdk.rest.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import open.vincentf13.sdk.auth.auth.PublicAPI;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Position Maintenance", description = "持倉維護接口")
@RequestMapping("/api/position/maintenance")
public interface PositionMaintenanceApi {

    @Operation(summary = "重新加載持倉快取", description = "觸發 InstrumentCache、MarkPriceCache 與 RiskLimitCache 的重新載入")
    @PostMapping("/reload-caches")
    @PublicAPI
    OpenApiResponse<Void> reloadCaches();
}
