package open.vincentf13.exchange.risk.controller;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.risk.margin.sdk.rest.api.LeveragePrecheckRequest;
import open.vincentf13.exchange.risk.margin.sdk.rest.api.LeveragePrecheckResponse;
import open.vincentf13.exchange.risk.margin.sdk.rest.api.RiskMarginApi;
import open.vincentf13.exchange.risk.service.LeveragePrecheckService;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class RiskMarginController implements RiskMarginApi {

    private final LeveragePrecheckService leveragePrecheckService;

    @Override
    public OpenApiResponse<LeveragePrecheckResponse> precheckLeverage(LeveragePrecheckRequest request) {
        return OpenApiResponse.success(leveragePrecheckService.precheck(request));
    }
}
