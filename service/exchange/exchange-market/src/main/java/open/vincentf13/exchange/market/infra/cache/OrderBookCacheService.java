package open.vincentf13.exchange.market.infra.cache;

import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.model.OrderUpdate;
import open.vincentf13.exchange.market.domain.model.OrderBookSnapshot;
import open.vincentf13.exchange.market.sdk.rest.api.dto.OrderBookLevel;
import open.vincentf13.sdk.core.OpenBigDecimal;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderBookCacheService {
    
    private final Map<Long, OrderBookSnapshot> cache = new ConcurrentHashMap<>();
    // 為了維護 L2，我們需要存儲每個 OrderId 的當前狀態，以便計算 Price Level 的變化
    private final Map<Long, Map<Long, OrderState>> orderStates = new ConcurrentHashMap<>();

    public void reset() {
        cache.clear();
        orderStates.clear();
    }

    private record OrderState(BigDecimal price, BigDecimal quantity, open.vincentf13.exchange.common.sdk.enums.OrderSide side) {}
    
    public void applyUpdates(Long instrumentId, List<OrderUpdate> updates, Instant updatedAt) {
        if (instrumentId == null || updates == null) {
            return;
        }
        
        Map<Long, OrderState> instrumentOrders = orderStates.computeIfAbsent(instrumentId, k -> new ConcurrentHashMap<>());
        
        for (OrderUpdate update : updates) {
            if (update.getRemainingQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                instrumentOrders.remove(update.getOrderId());
            } else {
                instrumentOrders.put(update.getOrderId(), new OrderState(update.getPrice(), update.getRemainingQuantity(), update.getSide()));
            }
        }
        
        // 重新計算 L2
        rebuildL2(instrumentId, instrumentOrders, updatedAt);
    }

    private void rebuildL2(Long instrumentId, Map<Long, OrderState> orders, Instant updatedAt) {
        TreeMap<BigDecimal, BigDecimal> bids = new TreeMap<>(Comparator.reverseOrder());
        TreeMap<BigDecimal, BigDecimal> asks = new TreeMap<>();
        
        for (OrderState os : orders.values()) {
            TreeMap<BigDecimal, BigDecimal> target = (os.side() == OrderSide.BUY) ? bids : asks;
            target.merge(os.price(), os.quantity(), BigDecimal::add);
        }
        
        List<OrderBookLevel> bidLevels = bids.entrySet().stream()
                .limit(20)
                .map(e -> OrderBookLevel.builder()
                        .price(e.getKey())
                        .quantity(OpenBigDecimal.normalizeDecimal(e.getValue()))
                        .build())
                .toList();
        List<OrderBookLevel> askLevels = asks.entrySet().stream()
                .limit(20)
                .map(e -> OrderBookLevel.builder()
                        .price(e.getKey())
                        .quantity(OpenBigDecimal.normalizeDecimal(e.getValue()))
                        .build())
                .toList();
        
        BigDecimal bestBid = bidLevels.isEmpty() ? null : bidLevels.get(0).getPrice();
        BigDecimal bestAsk = askLevels.isEmpty() ? null : askLevels.get(0).getPrice();
        BigDecimal mid = (bestBid != null && bestAsk != null) ? OpenBigDecimal.normalizeDecimal(bestBid.add(bestAsk).divide(BigDecimal.valueOf(2))) : null;

        OrderBookSnapshot snapshot = OrderBookSnapshot.builder()
                .instrumentId(instrumentId)
                .bids(bidLevels)
                .asks(askLevels)
                .bestBid(bestBid)
                .bestAsk(bestAsk)
                .midPrice(mid)
                .updatedAt(updatedAt)
                .build();
        
        cache.put(instrumentId, snapshot);
        
        /**
         TODO 持久化 與 啟動恢復
         */
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
                                .bestAsk(BigDecimal.ZERO)
                                .bestBid(BigDecimal.ZERO)
                                .midPrice(BigDecimal.ZERO)
                                .updatedAt(Instant.now())
                                .build();
    }
}