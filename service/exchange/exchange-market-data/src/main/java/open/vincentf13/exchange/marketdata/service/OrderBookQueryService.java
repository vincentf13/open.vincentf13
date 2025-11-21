package open.vincentf13.exchange.marketdata.service;

import lombok.RequiredArgsConstructor;
import open.vincentf13.exchange.market.sdk.rest.api.dto.OrderBookLevel;
import open.vincentf13.exchange.market.sdk.rest.api.dto.OrderBookResponse;
import open.vincentf13.exchange.marketdata.domain.model.OrderBookSnapshot;
import open.vincentf13.exchange.marketdata.infra.cache.OrderBookCacheService;
import org.springframework.stereotype.Service;

import java.util.List;
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
        return Optional.of(map(snapshot));
    }

    private OrderBookResponse map(OrderBookSnapshot snapshot) {
        return OrderBookResponse.builder()
                .instrumentId(snapshot.getInstrumentId())
                .bids(mapLevels(snapshot.getBids()))
                .asks(mapLevels(snapshot.getAsks()))
                .bestBid(snapshot.getBestBid())
                .bestAsk(snapshot.getBestAsk())
                .midPrice(snapshot.getMidPrice())
                .updatedAt(snapshot.getUpdatedAt())
                .build();
    }

    private List<OrderBookLevel> mapLevels(List<OrderBookLevel> levels) {
        if (levels == null || levels.isEmpty()) {
            return List.of();
        }
        return List.copyOf(levels);
    }
}
