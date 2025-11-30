package open.vincentf13.exchange.risk.controller;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.risk.domain.model.RiskLimit;
import open.vincentf13.exchange.risk.margin.sdk.rest.api.LeveragePrecheckRequest;
import open.vincentf13.exchange.risk.margin.sdk.rest.api.LeveragePrecheckResponse;
import open.vincentf13.exchange.risk.margin.sdk.rest.api.RiskLimitResponse;
import open.vincentf13.exchange.risk.margin.sdk.rest.api.RiskMarginApi;
import open.vincentf13.exchange.risk.service.RiskLimitQueryService;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class RiskMarginController implements RiskMarginApi {

    private final RiskLimitQueryService riskLimitQueryService;

    
   @Override
    public OpenApiResponse<LeveragePrecheckResponse> precheckLeverage(LeveragePrecheckRequest request) {
        // TODO
        return OpenApiResponse.success(null);
    }
    @Override
    public OpenApiResponse<RiskLimitResponse> getRiskLimit(Long instrumentId) {
        RiskLimit riskLimit = riskLimitQueryService.getRiskLimitByInstrumentId(instrumentId);
        return OpenApiResponse.success(new RiskLimitResponse(
                riskLimit.getInstrumentId(),
                riskLimit.getInitialMarginRate(),
                riskLimit.getMaxLeverage(),
                riskLimit.getMaintenanceMarginRate(),
                riskLimit.getLiquidationFeeRate(),
                riskLimit.getPositionSizeMin(),
                riskLimit.getPositionSizeMax(),
                riskLimit.getMaxPositionValue(),
                riskLimit.getMaxOrderValue(),
                riskLimit.getActive(),
                riskLimit.getUpdatedAt()
        ));
    }
}
