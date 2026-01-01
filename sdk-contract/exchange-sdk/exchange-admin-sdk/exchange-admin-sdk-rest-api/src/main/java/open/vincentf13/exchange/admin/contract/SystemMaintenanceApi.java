package open.vincentf13.exchange.admin.contract;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import open.vincentf13.sdk.auth.auth.PublicAPI;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "System Maintenance", description = "系統維護接口")
@RequestMapping("/api/admin/system")
public interface SystemMaintenanceApi {

    @Operation(summary = "重置系統數據", description = "刪除 Kafka 所有 Topic 數據，並清空指定的業務數據表")
    @PostMapping("/reset-data")
    @PublicAPI
    OpenApiResponse<Void> resetSystemData();
}
