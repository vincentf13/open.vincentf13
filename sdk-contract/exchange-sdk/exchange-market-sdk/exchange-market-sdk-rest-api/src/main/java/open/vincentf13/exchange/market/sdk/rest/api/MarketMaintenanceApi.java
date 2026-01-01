package open.vincentf13.exchange.market.sdk.rest.api;

import io.swagger.v3.oas.annotations.Operation;
import open.vincentf13.sdk.auth.auth.PublicAPI;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/market/maintenance")
public interface MarketMaintenanceApi {

    @PostMapping("/reset")
    @PublicAPI
    OpenApiResponse<Void> reset();
}
