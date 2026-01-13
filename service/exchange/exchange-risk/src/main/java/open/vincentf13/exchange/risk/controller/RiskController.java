package open.vincentf13.exchange.risk.controller;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.risk.domain.model.RiskLimit;
import open.vincentf13.exchange.risk.sdk.rest.api.OrderPrecheckRequest;
import open.vincentf13.exchange.risk.sdk.rest.api.OrderPrecheckResponse;
import open.vincentf13.exchange.risk.sdk.rest.api.RiskApi;
import open.vincentf13.exchange.risk.sdk.rest.api.RiskLimitResponse;
import open.vincentf13.exchange.risk.service.OrderPrecheckService;
import open.vincentf13.exchange.risk.service.RiskLimitQueryService;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class RiskController implements RiskApi {

  private final RiskLimitQueryService riskLimitQueryService;
  private final OrderPrecheckService orderPrecheckService;

  @Override
  public OpenApiResponse<RiskLimitResponse> getRiskLimit(@NotNull Long instrumentId) {
    RiskLimit riskLimit = riskLimitQueryService.getRiskLimitByInstrumentId(instrumentId);
    return OpenApiResponse.success(
        new RiskLimitResponse(
            riskLimit.getInstrumentId(),
            riskLimit.getInitialMarginRate(),
            riskLimit.getMaxLeverage(),
            riskLimit.getMaintenanceMarginRate(),
            riskLimit.getLiquidationFeeRate(),
            riskLimit.getActive(),
            riskLimit.getUpdatedAt()));
  }

  @Override
  public OpenApiResponse<OrderPrecheckResponse> precheckOrder(
      @NotNull OrderPrecheckRequest request) {
    return OpenApiResponse.success(orderPrecheckService.precheck(request));
  }
}
