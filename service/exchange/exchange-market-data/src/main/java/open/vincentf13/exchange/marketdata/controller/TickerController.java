package open.vincentf13.exchange.marketdata.controller;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.market.sdk.rest.api.MarketTickerApi;
import open.vincentf13.exchange.market.sdk.rest.api.dto.TickerResponse;
import open.vincentf13.exchange.marketdata.service.TickerQueryService;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market/tickers")
@RequiredArgsConstructor
public class TickerController implements MarketTickerApi {

    private final TickerQueryService tickerQueryService;

    @Override
    public OpenApiResponse<TickerResponse> getTicker(@PathVariable("instrumentId") Long instrumentId) {
        return OpenApiResponse.success(tickerQueryService.getTicker(instrumentId));
    }
}
