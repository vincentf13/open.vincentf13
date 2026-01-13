package open.vincentf13.exchange.matching.sdk.rest.api;

import open.vincentf13.sdk.auth.auth.PublicAPI;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.PostMapping;

public interface MatchingMaintenanceApi {

  @PostMapping("/reset")
  @PublicAPI
  OpenApiResponse<Void> reset();
}
