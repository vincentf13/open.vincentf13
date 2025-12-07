package open.vincentf13.exchange.risk.sdk.rest.api;

import jakarta.validation.constraints.NotNull;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Validated
public interface RiskApi {

    @GetMapping("/limits/{instrumentId}")
    OpenApiResponse<RiskLimitResponse> getRiskLimit(@PathVariable @NotNull Long instrumentId);
}
