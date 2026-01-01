package open.vincentf13.exchange.order.sdk.rest.api;

import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.PostMapping;

public interface OrderMaintenanceApi {

    @PostMapping("/reset")
    OpenApiResponse<Void> reset();
}
