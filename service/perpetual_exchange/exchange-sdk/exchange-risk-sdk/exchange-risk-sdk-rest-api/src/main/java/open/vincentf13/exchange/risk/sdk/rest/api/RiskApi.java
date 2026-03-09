package open.vincentf13.exchange.risk.sdk.rest.api;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import open.vincentf13.sdk.auth.auth.PublicAPI;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Validated
public interface RiskApi {

  @GetMapping("/limits/{instrumentId}")
  @PublicAPI
  OpenApiResponse<RiskLimitResponse> getRiskLimit(@PathVariable @NotNull Long instrumentId);

  @GetMapping("/limits")
  @PublicAPI
  OpenApiResponse<List<RiskLimitResponse>> list(
      @RequestParam(required = false) Long instrumentId);

  @PostMapping("/orders/precheck")
  OpenApiResponse<OrderPrecheckResponse> precheckOrder(
      @RequestBody @NotNull OrderPrecheckRequest request);
}
