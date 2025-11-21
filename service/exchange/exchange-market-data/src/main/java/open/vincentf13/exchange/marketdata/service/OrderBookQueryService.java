package open.vincentf13.exchange.marketdata.service;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.market.sdk.rest.api.dto.OrderBookResponse;
import open.vincentf13.exchange.marketdata.domain.model.OrderBookSnapshot;
import open.vincentf13.exchange.marketdata.infra.cache.OrderBookCacheService;
import open.vincentf13.sdk.core.OpenMapstruct;
import org.springframework.stereotype.Service;

import java.util.Optional;

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
        return Optional.ofNullable(OpenMapstruct.map(snapshot, OrderBookResponse.class));
    }
}
