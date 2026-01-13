package open.vincentf13.exchange.matching.sdk.rest.api;

import java.util.List;
import open.vincentf13.exchange.matching.sdk.rest.dto.TradeResponse;
import open.vincentf13.sdk.auth.auth.Jwt;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Validated
public interface TradeApi {

  @GetMapping
  @Jwt
  OpenApiResponse<List<TradeResponse>> listByOrderId(@RequestParam("orderId") Long orderId);

  @GetMapping("/by-instrument")
  @Jwt
  OpenApiResponse<List<TradeResponse>> listByInstrument(
      @RequestParam("instrumentId") Long instrumentId);
}
