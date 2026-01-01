package open.vincentf13.exchange.market.controller;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.market.infra.cache.KlineAggregationService;
import open.vincentf13.exchange.market.infra.cache.MarkPriceCacheService;
import open.vincentf13.exchange.market.infra.cache.OrderBookCacheService;
import open.vincentf13.exchange.market.infra.cache.TickerStatsCacheService;
import open.vincentf13.exchange.market.sdk.rest.api.MarketMaintenanceApi;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market/maintenance")
@RequiredArgsConstructor
public class MarketMaintenanceController implements MarketMaintenanceApi {

    private final KlineAggregationService klineAggregationService;
    private final MarkPriceCacheService markPriceCacheService;
    private final OrderBookCacheService orderBookCacheService;
    private final TickerStatsCacheService tickerStatsCacheService;

    @Override
    public OpenApiResponse<Void> reset() {
        klineAggregationService.reset();
        markPriceCacheService.reset();
        orderBookCacheService.reset();
        tickerStatsCacheService.reset();
        return OpenApiResponse.success(null);
    }
}
