package open.vincentf13.exchange.marketdata.infra.cache;

import lombok.extern.slf4j.Slf4j;
import open.vincentf13.exchange.marketdata.domain.model.OrderBookSnapshot;
import open.vincentf13.exchange.marketdata.domain.model.OrderBookSnapshot.OrderBookLevel;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class OrderBookCacheService {

    private final Map<Long, OrderBookSnapshot> cache = new ConcurrentHashMap<>();

    public void update(Long instrumentId,
                       List<OrderBookLevel> bids,
                       List<OrderBookLevel> asks,
                       BigDecimal bestBid,
                       BigDecimal bestAsk,
                       BigDecimal midPrice,
                       Instant updatedAt) {
        if (instrumentId == null) {
            return;
        }
        List<OrderBookLevel> sortedBids = bids == null ? List.of() : bids.stream()
                .sorted(Comparator.comparing(OrderBookLevel::getPrice).reversed())
                .toList();
        List<OrderBookLevel> sortedAsks = asks == null ? List.of() : asks.stream()
                .sorted(Comparator.comparing(OrderBookLevel::getPrice))
                .toList();

        BigDecimal resolvedBestBid = bestBid != null
                ? bestBid
                : sortedBids.stream().findFirst().map(OrderBookLevel::getPrice).orElse(null);
        BigDecimal resolvedBestAsk = bestAsk != null
                ? bestAsk
                : sortedAsks.stream().findFirst().map(OrderBookLevel::getPrice).orElse(null);
        BigDecimal resolvedMid = midPrice;
        if (resolvedMid == null && resolvedBestBid != null && resolvedBestAsk != null) {
            resolvedMid = resolvedBestBid.add(resolvedBestAsk).divide(BigDecimal.valueOf(2));
        }
        Instant resolvedUpdatedAt = updatedAt != null ? updatedAt : Instant.now();

        OrderBookSnapshot snapshot = OrderBookSnapshot.builder()
                .instrumentId(instrumentId)
                .bids(sortedBids)
                .asks(sortedAsks)
                .bestBid(resolvedBestBid)
                .bestAsk(resolvedBestAsk)
                .midPrice(resolvedMid)
                .updatedAt(resolvedUpdatedAt)
                .build();
        cache.put(instrumentId, snapshot);
    }

    public OrderBookSnapshot get(Long instrumentId) {
        if (instrumentId == null) {
            return null;
        }
        return cache.computeIfAbsent(instrumentId, this::createDefaultSnapshot);
    }

    private OrderBookSnapshot createDefaultSnapshot(Long instrumentId) {
        return OrderBookSnapshot.builder()
                .instrumentId(instrumentId)
                .bids(List.of())
                .asks(List.of())
                .updatedAt(Instant.now())
                .build();
    }
}
