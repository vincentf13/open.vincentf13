package open.vincentf13.exchange.risk.margin.sdk.rest.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@Validated
public interface RiskMarginApi {

    @PostMapping("/precheck/leverage")
    OpenApiResponse<LeveragePrecheckResponse> precheckLeverage(@Valid @RequestBody LeveragePrecheckRequest request);

    @GetMapping("/limits/{instrumentId}")
    OpenApiResponse<RiskLimitResponse> getRiskLimit(@PathVariable @NotNull Long instrumentId);
}
