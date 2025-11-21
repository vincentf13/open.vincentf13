package open.vincentf13.exchange.risk.margin.sdk.rest.api;

import jakarta.validation.Valid;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Validated
public interface RiskMarginApi {

    @PostMapping("/precheck/leverage")
    OpenApiResponse<LeveragePrecheckResponse> precheckLeverage(@Valid @RequestBody LeveragePrecheckRequest request);
}
