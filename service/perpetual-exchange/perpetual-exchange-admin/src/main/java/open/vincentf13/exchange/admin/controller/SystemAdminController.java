package open.vincentf13.exchange.admin.controller;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.admin.contract.SystemMaintenanceApi;
import open.vincentf13.exchange.admin.service.SystemMaintenanceCommandService;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/system")
public class SystemAdminController implements SystemMaintenanceApi {

  private final SystemMaintenanceCommandService maintenanceService;

  @Override
  public OpenApiResponse<Void> resetSystemData() {
    maintenanceService.resetData();
    return OpenApiResponse.success(null);
  }
}
