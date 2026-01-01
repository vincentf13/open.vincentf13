import io.swagger.v3.oas.annotations.Operation;
import open.vincentf13.sdk.auth.auth.PublicAPI;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/market/maintenance")
public interface MarketMaintenanceApi {

    @Operation(summary = "重置市場快取", description = "清空 K 線、標記價格、訂單簿及 Ticker 統計的所有內存狀態")
    @PostMapping("/reset")
    @PublicAPI
    OpenApiResponse<Void> reset();
}
