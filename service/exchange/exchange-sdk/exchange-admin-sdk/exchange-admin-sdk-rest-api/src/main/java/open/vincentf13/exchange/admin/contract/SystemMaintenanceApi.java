package open.vincentf13.exchange.admin.contract;

import open.vincentf13.sdk.auth.auth.PublicAPI;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.PostMapping;

public interface SystemMaintenanceApi {

  @PostMapping("/reset-data")
  @PublicAPI
  OpenApiResponse<Void> resetSystemData();
}
