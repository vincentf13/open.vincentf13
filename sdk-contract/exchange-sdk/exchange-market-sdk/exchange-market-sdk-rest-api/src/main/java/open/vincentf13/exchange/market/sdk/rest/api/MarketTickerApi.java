package open.vincentf13.exchange.market.sdk.rest.api;

import jakarta.validation.constraints.NotNull;
import open.vincentf13.exchange.market.sdk.rest.api.dto.TickerResponse;
import open.vincentf13.sdk.auth.auth.PublicAPI;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Validated
public interface MarketTickerApi {

    @GetMapping("/{instrumentId}")
    @PublicAPI
    OpenApiResponse<TickerResponse> getTicker(@PathVariable("instrumentId") @NotNull Long instrumentId);
}
