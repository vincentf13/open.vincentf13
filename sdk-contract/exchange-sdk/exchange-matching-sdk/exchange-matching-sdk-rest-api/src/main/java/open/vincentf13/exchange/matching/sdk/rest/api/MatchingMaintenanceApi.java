import io.swagger.v3.oas.annotations.Operation;
import open.vincentf13.sdk.auth.auth.PublicAPI;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/matching/maintenance")
public interface MatchingMaintenanceApi {

    @Operation(summary = "重置撮合引擎", description = "清空所有 WAL/Snapshot 檔案，重置內存狀態並重新加載快取")
    @PostMapping("/reset")
    @PublicAPI
    OpenApiResponse<Void> reset();
}
