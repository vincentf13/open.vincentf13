package open.vincentf13.exchange.market.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.market.domain.model.OrderBookSnapshot;
import open.vincentf13.exchange.market.infra.cache.OrderBookCacheService;
import open.vincentf13.exchange.market.sdk.rest.api.dto.OrderBookResponse;
import open.vincentf13.sdk.core.mapper.OpenObjectMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderBookQueryService {

  private final OrderBookCacheService orderBookCacheService;

  public Optional<OrderBookResponse> getOrderBook(Long instrumentId) {
    if (instrumentId == null) {
      return Optional.empty();
    }
    OrderBookSnapshot snapshot = orderBookCacheService.get(instrumentId);
    if (snapshot == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(OpenObjectMapper.convert(snapshot, OrderBookResponse.class));
  }
}
