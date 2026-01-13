package open.vincentf13.exchange.market.service;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.market.infra.cache.TickerStatsCacheService;
import open.vincentf13.exchange.market.sdk.rest.api.dto.TickerResponse;
import open.vincentf13.sdk.core.object.mapper.OpenObjectMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TickerQueryService {

  private final TickerStatsCacheService tickerStatsCacheService;

  public TickerResponse getTicker(Long instrumentId) {
    if (instrumentId == null) {
      throw new IllegalArgumentException("instrumentId must not be null");
    }
    return OpenObjectMapper.convert(
        tickerStatsCacheService.get(instrumentId), TickerResponse.class);
  }
}
