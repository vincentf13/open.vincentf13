package open.vincentf13.exchange.market.sdk.rest.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.market.sdk.rest.api.dto.KlineResponse;
import open.vincentf13.exchange.market.sdk.rest.api.dto.OrderBookResponse;
import open.vincentf13.exchange.market.sdk.rest.api.dto.TickerResponse;
import open.vincentf13.sdk.auth.auth.PublicAPI;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Validated
public interface MarketApi {

    @GetMapping("/tickers/{instrumentId}")
    @PublicAPI
    OpenApiResponse<TickerResponse> getTicker(@PathVariable("instrumentId") @NotNull Long instrumentId);

    @GetMapping("/orderbook/{instrumentId}")
    @PublicAPI
    OpenApiResponse<OrderBookResponse> getOrderBook(@PathVariable("instrumentId") @NotNull Long instrumentId);

    @GetMapping("/kline")
    @PublicAPI
    OpenApiResponse<List<KlineResponse>> getKlines(@RequestParam("instrumentId") @NotNull Long instrumentId,
                                                   @RequestParam("period") @NotBlank String period,
                                                   @RequestParam(value = "limit", required = false) @Min(1) Integer limit);
}
