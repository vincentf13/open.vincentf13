package open.vincentf13.exchange.market.controller;

import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.market.sdk.rest.api.MarketApi;
import open.vincentf13.exchange.market.sdk.rest.api.dto.KlineResponse;
import open.vincentf13.exchange.market.sdk.rest.api.dto.MarkPriceResponse;
import open.vincentf13.exchange.market.sdk.rest.api.dto.OrderBookResponse;
import open.vincentf13.exchange.market.sdk.rest.api.dto.TickerResponse;
import open.vincentf13.exchange.market.service.KlineQueryService;
import open.vincentf13.exchange.market.service.MarkPriceQueryService;
import open.vincentf13.exchange.market.service.OrderBookQueryService;
import open.vincentf13.exchange.market.service.TickerQueryService;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketController implements MarketApi {

  private final TickerQueryService tickerQueryService;
  private final OrderBookQueryService orderBookQueryService;
  private final KlineQueryService klineQueryService;
  private final MarkPriceQueryService markPriceQueryService;

  @Override
  public OpenApiResponse<TickerResponse> getTicker(Long instrumentId) {
    return OpenApiResponse.success(tickerQueryService.getTicker(instrumentId));
  }

  @Override
  public OpenApiResponse<OrderBookResponse> getOrderBook(Long instrumentId) {
    return orderBookQueryService
        .getOrderBook(instrumentId)
        .map(OpenApiResponse::success)
        .orElseGet(
            () ->
                OpenApiResponse.success(
                    OrderBookResponse.builder().instrumentId(instrumentId).build()));
  }

  @Override
  public OpenApiResponse<List<KlineResponse>> getKlines(
      Long instrumentId, String period, Integer limit) {
    return OpenApiResponse.success(klineQueryService.getKlines(instrumentId, period, limit));
  }

  @Override
  public OpenApiResponse<MarkPriceResponse> getMarkPrice(Long instrumentId) {
    return markPriceQueryService
        .getMarkPrice(instrumentId)
        .map(OpenApiResponse::success)
        .orElseGet(
            () ->
                OpenApiResponse.success(
                    MarkPriceResponse.builder()
                        .instrumentId(instrumentId)
                        .markPrice(null)
                        .markPriceChangeRate(BigDecimal.ONE)
                        .build()));
  }
}
