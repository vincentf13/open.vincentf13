package open.vincentf13.exchange.matching.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.matching.sdk.rest.api.TradeApi;
import open.vincentf13.exchange.matching.sdk.rest.dto.TradeResponse;
import open.vincentf13.exchange.matching.service.TradeQueryService;
import open.vincentf13.sdk.auth.jwt.OpenJwtLoginUserHolder;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeController implements TradeApi {

  private final TradeQueryService tradeQueryService;

  @Override
  public OpenApiResponse<List<TradeResponse>> listByOrderId(Long orderId) {
    Long userId = OpenJwtLoginUserHolder.currentUserId();
    if (userId == null) {
      throw new IllegalArgumentException("Missing user context");
    }
    return OpenApiResponse.success(tradeQueryService.listByOrderId(userId, orderId));
  }

  @Override
  public OpenApiResponse<List<TradeResponse>> listByInstrument(Long instrumentId) {
    Long userId = OpenJwtLoginUserHolder.currentUserId();
    if (userId == null) {
      throw new IllegalArgumentException("Missing user context");
    }
    return OpenApiResponse.success(tradeQueryService.listByInstrument(userId, instrumentId));
  }
}
