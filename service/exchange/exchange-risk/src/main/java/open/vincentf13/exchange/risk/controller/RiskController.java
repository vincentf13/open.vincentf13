package open.vincentf13.exchange.risk.controller;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.risk.domain.model.RiskLimit;
import open.vincentf13.exchange.risk.sdk.rest.api.RiskLimitResponse;
import open.vincentf13.exchange.risk.sdk.rest.api.RiskApi;
import open.vincentf13.exchange.risk.service.RiskLimitQueryService;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class RiskController implements RiskApi {

    private final RiskLimitQueryService riskLimitQueryService;

    @Override
    public OpenApiResponse<RiskLimitResponse> getRiskLimit(@NotNull Long instrumentId) {
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
