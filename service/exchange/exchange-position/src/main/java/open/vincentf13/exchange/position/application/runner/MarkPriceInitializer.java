package open.vincentf13.exchange.position.application.runner;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.admin.contract.dto.InstrumentSummaryResponse;
import open.vincentf13.exchange.market.sdk.rest.api.MarketApi;
import open.vincentf13.exchange.market.sdk.rest.api.dto.MarkPriceResponse;
import open.vincentf13.exchange.position.domain.service.PositionDomainService;
import open.vincentf13.exchange.position.infra.cache.InstrumentCache;
import open.vincentf13.exchange.position.infra.cache.MarkPriceCache;
import open.vincentf13.sdk.spring.mvc.OpenApiResponse;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class MarkPriceInitializer implements ApplicationRunner {

    private final MarketApi marketApi;
    private final PositionDomainService positionDomainService;
    private final MarkPriceCache markPriceCache;
    private final InstrumentCache instrumentCache;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting MarkPriceInitializer...");
        for (InstrumentSummaryResponse instrument : instrumentCache.getAll()) {
            Long instrumentId = instrument.instrumentId();
            try {
                OpenApiResponse<MarkPriceResponse> response = marketApi.getMarkPrice(instrumentId);
                if (response.isSuccess() && response.data() != null) {
                    MarkPriceResponse data = response.data();
                    log.info("Fetched mark price for instrument {}: {}", instrumentId, data.getMarkPrice());

                    markPriceCache.update(data.getInstrumentId(), data.getMarkPrice(), data.getCalculatedAt());
                    positionDomainService.updateMarkPrice(data.getInstrumentId(), data.getMarkPrice());
                } else {
                    log.warn("Failed to fetch mark price for instrument {}: {}", instrumentId, response.message());
                }
            } catch (Exception e) {
                log.error("Error fetching mark price for instrument {}", instrumentId, e);
            }
        }
        log.info("MarkPriceInitializer completed.");
    }
}
