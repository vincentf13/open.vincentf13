package open.vincentf13.exchange.matching.domain.order.book;

import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.model.OrderUpdate;
import open.vincentf13.exchange.matching.domain.instrument.Instrument;
import open.vincentf13.exchange.matching.domain.match.result.MatchResult;
import open.vincentf13.exchange.matching.domain.match.result.Trade;
import open.vincentf13.exchange.matching.infra.cache.InstrumentCache;
import open.vincentf13.sdk.core.OpenBigDecimal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public class OrderBook {
    
    private static final int PROCESSED_CACHE_SIZE = 1_000_000;
    private final TreeMap<BigDecimal, Deque<Order>> asks = new TreeMap<>(BigDecimal::compareTo);
    private final TreeMap<BigDecimal, Deque<Order>> bids = new TreeMap<>(Comparator.reverseOrder());
    private final Map<Long, Order> orderIndex = new HashMap<>();
    private final Map<Long, Boolean> processedOrderIds = new LinkedHashMap<>(1024, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
            return size() > PROCESSED_CACHE_SIZE;
        }
    };
    
    public MatchResult match(Order taker) {
        MatchResult result = new MatchResult(taker);
        BigDecimal remaining = taker.getQuantity();
        TreeMap<BigDecimal, Deque<Order>> targetBook = taker.isBuy() ? asks : bids;
        Iterator<Map.Entry<BigDecimal, Deque<Order>>> iterator = targetBook.entrySet().iterator();
        
        while (remaining.compareTo(BigDecimal.ZERO) > 0 && iterator.hasNext()) {
            Map.Entry<BigDecimal, Deque<Order>> entry = iterator.next();
            BigDecimal price = entry.getKey();
            if (!isCrossed(taker, price)) {
                break;
            }
            Deque<Order> queue = entry.getValue();
            Iterator<Order> makerIterator = new ArrayList<>(queue).iterator();
            while (remaining.compareTo(BigDecimal.ZERO) > 0 && makerIterator.hasNext()) {
                Order maker = makerIterator.next();
                BigDecimal fillQty = remaining.min(maker.getQuantity());
                Trade trade = buildTrade(taker, maker, price, fillQty);
                result.addTrade(trade);
                
                BigDecimal makerRemaining = maker.getQuantity().subtract(fillQty);
                result.addUpdate(OrderUpdate.builder()
                                            .orderId(maker.getOrderId())
                                            .price(maker.getPrice())
                                            .side(maker.getSide())
                                            .remainingQuantity(OpenBigDecimal.normalizeDecimal(makerRemaining.max(BigDecimal.ZERO)))
                                            .isTaker(false)
                                            .build());
                remaining = remaining.subtract(fillQty);
            }
        }
        
        result.addUpdate(OrderUpdate.builder()
                                    .orderId(taker.getOrderId())
                                    .price(taker.getPrice())
                                    .side(taker.getSide())
                                    .remainingQuantity(OpenBigDecimal.normalizeDecimal(remaining.max(BigDecimal.ZERO)))
                                    .isTaker(true)
                                    .build());
        return result;
    }
    
    /**
      根據撮合結果更新簿內訂單，必須在 WAL 已持久化後才執行，避免未落盤狀態污染記憶體。
     */
    public void apply(MatchResult result) {
        // 根據撮合結果更新簿內訂單：移除成交完畢、保留剩餘量
        for (OrderUpdate update : result.getUpdates()) {
            if (update.isTaker()) {
                if (update.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0
                            && result.getTakerOrder().getPrice() != null) {
                    Order taker = result.getTakerOrder();
                    taker.setQuantity(update.getRemainingQuantity());
                    insert(taker);
                }
                continue;
            }
            Order maker = orderIndex.get(update.getOrderId());
            if (maker == null) {
                continue;
            }
            maker.setQuantity(update.getRemainingQuantity());
            if (maker.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                remove(maker);
            }
        }
    }
    
    public void insert(Order order) {
        TreeMap<BigDecimal, Deque<Order>> book = order.isBuy() ? bids : asks;
        Deque<Order> queue = book.computeIfAbsent(order.getPrice(), key -> new ArrayDeque<>());
        queue.addLast(order);
        orderIndex.put(order.getOrderId(), order);
    }
    
    public void restore(Order order) {
        insert(order);
    }
    
    public List<Order> dumpOpenOrders() {
        List<Order> orders = new ArrayList<>(orderIndex.size());
        // 先輸出 bids (price DESC) 保持掛單順序，再輸出 asks (price ASC)
        bids.values().forEach(queue -> orders.addAll(queue));
        asks.values().forEach(queue -> orders.addAll(queue));
        return orders;
    }
    
    public boolean alreadyProcessed(Long orderId) {
        return orderId != null && processedOrderIds.containsKey(orderId);
    }
    
    public void markProcessed(Long orderId) {
        if (orderId != null) {
            processedOrderIds.put(orderId, Boolean.TRUE);
        }
    }
    
    public List<Long> dumpProcessedOrderIds() {
        return new ArrayList<>(processedOrderIds.keySet());
    }
    
    public void restoreProcessedIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return;
        }
        orderIds.forEach(id -> processedOrderIds.put(id, Boolean.TRUE));
    }
    
    private void remove(Order order) {
        TreeMap<BigDecimal, Deque<Order>> book = order.isBuy() ? bids : asks;
        Deque<Order> queue = book.get(order.getPrice());
        if (queue == null) {
            orderIndex.remove(order.getOrderId());
            return;
        }
        queue.removeIf(o -> o.getOrderId().equals(order.getOrderId()));
        if (queue.isEmpty()) {
            book.remove(order.getPrice());
        }
        orderIndex.remove(order.getOrderId());
    }
    
    private Trade buildTrade(Order taker,
                             Order maker,
                             BigDecimal price,
                             BigDecimal quantity) {
        
        Instrument instrument = InstrumentCache.getInstrument(taker.getInstrumentId());
        if (instrument == null) {
            throw new IllegalStateException("instrument cache missing for id " + taker.getInstrumentId());
        }
        
        AssetSymbol quoteAsset = instrument.getQuoteAsset();
        BigDecimal makerFee = instrument.getMakerFee();
        BigDecimal takerFee = instrument.getTakerFee();
        if (quoteAsset == null || makerFee == null || takerFee == null) {
            throw new IllegalStateException("instrument cache incomplete for id " + taker.getInstrumentId());
        }
        
        BigDecimal totalValue = OpenBigDecimal.normalizeDecimal(price.multiply(quantity));
        
        return Trade.builder()
                    .tradeId(null)
                    .instrumentId(taker.getInstrumentId())
                    .quoteAsset(quoteAsset)
                    .makerUserId(maker.getUserId())
                    .takerUserId(taker.getUserId())
                    .orderId(maker.getOrderId())
                    .counterpartyOrderId(taker.getOrderId())
                    .orderSide(maker.getSide())
                    .counterpartyOrderSide(taker.getSide())
                    .makerIntent(maker.getIntent())
                    .takerIntent(taker.getIntent())
                    .tradeType(taker.getTradeType())
                    .price(OpenBigDecimal.normalizeDecimal(price))
                    .quantity(OpenBigDecimal.normalizeDecimal(quantity))
                    .totalValue(totalValue)
                    .makerFee(totalValue.multiply(makerFee))
                    .takerFee(totalValue.multiply(takerFee))
                    .executedAt(Instant.now())
                    .build();
    }
    
    private boolean isCrossed(Order taker,
                              BigDecimal levelPrice) {
        if (taker.getPrice() == null) {
            return true;
        }
        return taker.isBuy()
               ? taker.getPrice().compareTo(levelPrice) >= 0
               : taker.getPrice().compareTo(levelPrice) <= 0;
    }
}
