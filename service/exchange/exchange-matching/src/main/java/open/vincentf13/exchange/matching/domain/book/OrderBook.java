package open.vincentf13.exchange.matching.domain.book;

import open.vincentf13.exchange.common.sdk.enums.AssetSymbol;
import open.vincentf13.exchange.common.sdk.enums.OrderSide;
import open.vincentf13.exchange.common.sdk.enums.PositionIntentType;
import open.vincentf13.exchange.matching.domain.model.MatchingOrder;
import open.vincentf13.exchange.matching.domain.model.OrderUpdate;
import open.vincentf13.exchange.matching.domain.model.Trade;
import open.vincentf13.exchange.matching.sdk.mq.enums.TradeType;
import open.vincentf13.exchange.matching.sdk.mq.event.OrderBookUpdatedEvent;
import open.vincentf13.sdk.core.OpenBigDecimal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class OrderBook {
    
    private final TreeMap<BigDecimal, Deque<MatchingOrder>> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<BigDecimal, Deque<MatchingOrder>> asks = new TreeMap<>(BigDecimal::compareTo);
    private final Map<Long, MatchingOrder> orderIndex = new HashMap<>();
    
    public MatchResult match(MatchingOrder taker) {
        MatchResult result = new MatchResult(taker);
        BigDecimal remaining = taker.getQuantity();
        TreeMap<BigDecimal, Deque<MatchingOrder>> targetBook = taker.isBuy() ? asks : bids;
        Iterator<Map.Entry<BigDecimal, Deque<MatchingOrder>>> iterator = targetBook.entrySet().iterator();
        
        while (remaining.compareTo(BigDecimal.ZERO) > 0 && iterator.hasNext()) {
            Map.Entry<BigDecimal, Deque<MatchingOrder>> entry = iterator.next();
            BigDecimal price = entry.getKey();
            if (!isCrossed(taker, price)) {
                break;
            }
            Deque<MatchingOrder> queue = entry.getValue();
            Iterator<MatchingOrder> makerIterator = new ArrayList<>(queue).iterator();
            while (remaining.compareTo(BigDecimal.ZERO) > 0 && makerIterator.hasNext()) {
                MatchingOrder maker = makerIterator.next();
                BigDecimal fillQty = remaining.min(maker.getQuantity());
                Trade trade = buildTrade(taker, maker, price, fillQty);
                result.addTrade(trade);
                
                BigDecimal makerRemaining = maker.getQuantity().subtract(fillQty);
                result.addUpdate(OrderUpdate.builder()
                                            .orderId(maker.getOrderId())
                                            .remainingQuantity(OpenBigDecimal.normalizeDecimal(makerRemaining.max(BigDecimal.ZERO)))
                                            .taker(false)
                                            .build());
                remaining = remaining.subtract(fillQty);
            }
        }
        
        result.addUpdate(OrderUpdate.builder()
                                    .orderId(taker.getOrderId())
                                    .remainingQuantity(OpenBigDecimal.normalizeDecimal(remaining.max(BigDecimal.ZERO)))
                                    .taker(true)
                                    .build());
        return result;
    }
    
    public void apply(MatchResult result) {
        for (OrderUpdate update : result.getUpdates()) {
            if (update.isTaker()) {
                if (update.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0
                    && result.getTakerOrder().getPrice() != null) {
                    MatchingOrder taker = result.getTakerOrder();
                    taker.setQuantity(update.getRemainingQuantity());
                    insert(taker);
                }
                continue;
            }
            MatchingOrder maker = orderIndex.get(update.getOrderId());
            if (maker == null) {
                continue;
            }
            maker.setQuantity(update.getRemainingQuantity());
            if (maker.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                remove(maker);
            }
        }
    }
    
    public void insert(MatchingOrder order) {
        TreeMap<BigDecimal, Deque<MatchingOrder>> book = order.isBuy() ? bids : asks;
        Deque<MatchingOrder> queue = book.computeIfAbsent(order.getPrice(), key -> new ArrayDeque<>());
        queue.addLast(order);
        orderIndex.put(order.getOrderId(), order);
    }
    
    public void restore(MatchingOrder order) {
        insert(order);
    }
    
    public OrderBookUpdatedEvent depthSnapshot(Long instrumentId,
                                               int depth) {
        List<OrderBookUpdatedEvent.OrderBookLevel> bidLevels = topLevels(bids, depth);
        List<OrderBookUpdatedEvent.OrderBookLevel> askLevels = topLevels(asks, depth);
        BigDecimal bestBid = bidLevels.isEmpty() ? null : bidLevels.get(0).price();
        BigDecimal bestAsk = askLevels.isEmpty() ? null : askLevels.get(0).price();
        BigDecimal mid = bestBid != null && bestAsk != null
                         ? OpenBigDecimal.normalizeDecimal(bestBid.add(bestAsk).divide(BigDecimal.valueOf(2)))
                         : null;
        return new OrderBookUpdatedEvent(instrumentId, bidLevels, askLevels, bestBid, bestAsk, mid, Instant.now());
    }
    
    public List<MatchingOrder> dumpOpenOrders() {
        return new ArrayList<>(orderIndex.values());
    }
    
    private List<OrderBookUpdatedEvent.OrderBookLevel> topLevels(Map<BigDecimal, Deque<MatchingOrder>> source,
                                                                 int depth) {
        List<OrderBookUpdatedEvent.OrderBookLevel> levels = new ArrayList<>(depth);
        for (Map.Entry<BigDecimal, Deque<MatchingOrder>> entry : source.entrySet()) {
            if (levels.size() >= depth) {
                break;
            }
            BigDecimal total = entry.getValue()
                                    .stream()
                                    .map(MatchingOrder::getQuantity)
                                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.compareTo(BigDecimal.ZERO) > 0) {
                levels.add(new OrderBookUpdatedEvent.OrderBookLevel(entry.getKey(),
                                                                    OpenBigDecimal.normalizeDecimal(total)));
            }
        }
        return levels;
    }
    
    private void remove(MatchingOrder order) {
        TreeMap<BigDecimal, Deque<MatchingOrder>> book = order.isBuy() ? bids : asks;
        Deque<MatchingOrder> queue = book.get(order.getPrice());
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
    
    private Trade buildTrade(MatchingOrder taker,
                             MatchingOrder maker,
                             BigDecimal price,
                             BigDecimal quantity) {
        OrderSide takerSide = taker.getSide();
        OrderSide makerSide = maker.getSide();
        PositionIntentType makerIntent = maker.getIntent() != null ? maker.getIntent() : PositionIntentType.INCREASE;
        PositionIntentType takerIntent = taker.getIntent() != null ? taker.getIntent() : PositionIntentType.INCREASE;
        return Trade.builder()
                    .tradeId(null)
                    .instrumentId(taker.getInstrumentId())
                    .quoteAsset(AssetSymbol.UNKNOWN)
                    .makerUserId(maker.getUserId())
                    .takerUserId(taker.getUserId())
                    .orderId(maker.getOrderId())
                    .counterpartyOrderId(taker.getOrderId())
                    .orderSide(makerSide)
                    .counterpartyOrderSide(takerSide)
                    .makerIntent(makerIntent)
                    .takerIntent(takerIntent)
                    .tradeType(TradeType.NORMAL)
                    .price(OpenBigDecimal.normalizeDecimal(price))
                    .quantity(OpenBigDecimal.normalizeDecimal(quantity))
                    .totalValue(OpenBigDecimal.normalizeDecimal(price.multiply(quantity)))
                    .makerFee(BigDecimal.ZERO)
                    .takerFee(BigDecimal.ZERO)
                    .executedAt(Instant.now())
                    .build();
    }
    
    private boolean isCrossed(MatchingOrder taker,
                              BigDecimal levelPrice) {
        if (taker.getPrice() == null) {
            return true;
        }
        return taker.isBuy()
               ? taker.getPrice().compareTo(levelPrice) >= 0
               : taker.getPrice().compareTo(levelPrice) <= 0;
    }
}
