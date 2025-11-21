package open.vincentf13.exchange.marketdata.controller;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.market.sdk.rest.api.MarketApi;
import open.vincentf13.exchange.market.sdk.rest.api.dto.OrderBookResponse;
import open.vincentf13.exchange.market.sdk.rest.api.dto.TickerResponse;
import open.vincentf13.exchange.marketdata.service.OrderBookQueryService;
import open.vincentf13.exchange.marketdata.service.TickerQueryService;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketController implements MarketApi {

    private final TickerQueryService tickerQueryService;
    private final OrderBookQueryService orderBookQueryService;

    @Override
    public OpenApiResponse<TickerResponse> getTicker(@PathVariable("instrumentId") Long instrumentId) {
        return OpenApiResponse.success(tickerQueryService.getTicker(instrumentId));
    }

    @Override
    public OpenApiResponse<OrderBookResponse> getOrderBook(@PathVariable("instrumentId") Long instrumentId) {
        return orderBookQueryService.getOrderBook(instrumentId)
                .map(OpenApiResponse::success)
                .orElseGet(() -> OpenApiResponse.success(OrderBookResponse.builder()
                        .instrumentId(instrumentId)
                        .build()));
    }
}
