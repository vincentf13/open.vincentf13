package open.vincentf13.service.spot.matching.engine;

import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.Trade;
import open.vincentf13.service.spot.sbe.OrderCreateDecoder;
import open.vincentf13.service.spot.sbe.Side;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import java.util.*;
import java.util.function.Supplier;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 內存訂單簿 (OrderBook)
 職責：領域狀態機，管理撮合、持久化同步與容器回收
 */
@Slf4j
public class OrderBook {
    private static final Int2ObjectHashMap<OrderBook> INSTANCES = new Int2ObjectHashMap<>();
    
    public static OrderBook get(int symbolId) {
        return INSTANCES.computeIfAbsent(symbolId, OrderBook::new);
    }

    private static final int INITIAL_DEQUE_CAPACITY = MatchingConfig.INITIAL_BOOK_LEVEL_CAPACITY;
    private static final int POOL_MAX_SIZE = MatchingConfig.INITIAL_POOL_SIZE;

    private final int symbolId;
    private final ChronicleMap<Long, Order> allOrdersDiskMap = Storage.self().orders();
    private final ChronicleMap<Long, Boolean> activeOrderIdDiskMap = Storage.self().activeOrders();
    private final ChronicleMap<Long, Trade> tradeHistoryDiskMap = Storage.self().trades();

    private final TreeMap<Long, Deque<Order>> bids = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<Long, Deque<Order>> asks = new TreeMap<>();
    private final Long2ObjectHashMap<Order> orderIndex = new Long2ObjectHashMap<>(MatchingConfig.INITIAL_BOOK_ORDER_COUNT, 0.5f); 

    private final Deque<Order> orderPool = new ArrayDeque<>(POOL_MAX_SIZE);
    private final Deque<Deque<Order>> dequePool = new ArrayDeque<>(1000);
    private final Trade reusableTrade = new Trade();
    private static final Order SCAN_REUSABLE = new Order();

    @FunctionalInterface public interface TradeFinalizer {
        void onMatch(Order maker, long price, long qty);
    }

    private OrderBook(int symbolId) {
        this.symbolId = symbolId;
        for (int i = 0; i < 1000; i++) {
            orderPool.add(new Order());
            dequePool.add(new ArrayDeque<>(INITIAL_DEQUE_CAPACITY));
        }
    }

    public void recoverOrder(Order o) {
        if (o == null || o.getStatus() >= 2) return;
        Order recovered = borrowOrder();
        copyOrder(o, recovered);
        add(recovered);
    }

    private void copyOrder(Order src, Order dst) {
        dst.setOrderId(src.getOrderId()); dst.setUserId(src.getUserId());
        dst.setSymbolId(src.getSymbolId()); dst.setPrice(src.getPrice());
        dst.setQty(src.getQty()); dst.setFilled(src.getFilled());
        dst.setSide(src.getSide()); dst.setStatus(src.getStatus());
        dst.setVersion(src.getVersion()); dst.setLastSeq(src.getLastSeq());
        dst.setClientOrderId(src.getClientOrderId());
    }

    private Order borrowOrder() {
        Order o = orderPool.pollFirst();
        return (o == null) ? new Order() : o;
    }

    public void releaseOrder(Order o) { if (o != null) orderPool.addLast(o); }

    public Order handleCreate(long orderId, OrderCreateDecoder sbe, long clientOrderId, long gwSeq, 
                             Supplier<Long> tradeIdSupplier, TradeFinalizer finalizer) {
        Order taker = borrowAndFill(orderId, sbe, clientOrderId, gwSeq);
        match(taker, gwSeq, sbe.timestamp(), tradeIdSupplier, finalizer);
        syncOrder(taker, gwSeq);
        return taker;
    }

    private Order borrowAndFill(long orderId, OrderCreateDecoder sbe, long clientOrderId, long gwSeq) {
        Order o = borrowOrder();
        o.setOrderId(orderId); o.setUserId(sbe.userId()); o.setSymbolId(sbe.symbolId());
        o.setPrice(sbe.price()); o.setQty(sbe.qty()); o.setFilled(0);
        o.setSide((byte)(sbe.side() == Side.BUY ? OrderSide.BUY : OrderSide.SELL));
        o.setStatus((byte)0); o.setVersion(1); o.setLastSeq(gwSeq);
        o.setClientOrderId(clientOrderId);
        return o;
    }

    public static void rebuildAll() {
        ChronicleMap<Long, Order> allOrders = Storage.self().orders();
        Storage.self().activeOrders().forEach((id, active) -> {
            Order o = allOrders.getUsing(id, SCAN_REUSABLE);
            if (o != null && o.getStatus() < 2) OrderBook.get(o.getSymbolId()).recoverOrder(o);
        });
    }

    private void match(Order taker, long gwSeq, long timestamp, Supplier<Long> tradeIdSupplier, TradeFinalizer finalizer) {
        final boolean isBuy = taker.getSide() == OrderSide.BUY;
        final TreeMap<Long, Deque<Order>> counterSide = isBuy ? asks : bids;
        final long takerPrice = taker.getPrice();

        while (taker.getQty() > taker.getFilled()) {
            if (counterSide.isEmpty()) break;
            final long bestPrice = counterSide.firstKey();
            if (isBuy ? (takerPrice < bestPrice) : (takerPrice > bestPrice)) break;

            final Deque<Order> makers = counterSide.get(bestPrice);
            while (makers != null && !makers.isEmpty() && taker.getQty() > taker.getFilled()) {
                Order maker = makers.peekFirst();
                if (!orderIndex.containsKey(maker.getOrderId())) {
                    releaseOrder(makers.pollFirst()); continue;
                }

                long matchQty = Math.min(taker.getQty() - taker.getFilled(), maker.getQty() - maker.getFilled());
                long tid = tradeIdSupplier.get();
                
                reusableTrade.setTradeId(tid); reusableTrade.setOrderId(maker.getOrderId());
                reusableTrade.setPrice(bestPrice); reusableTrade.setQty(matchQty);
                reusableTrade.setTime(timestamp); reusableTrade.setLastSeq(gwSeq);
                tradeHistoryDiskMap.put(tid, reusableTrade);

                maker.setFilled(maker.getFilled() + matchQty);
                taker.setFilled(taker.getFilled() + matchQty);
                finalizer.onMatch(maker, bestPrice, matchQty);

                if (maker.getFilled() == maker.getQty()) {
                    syncOrder(maker, gwSeq);
                    orderIndex.remove(maker.getOrderId());
                    releaseOrder(makers.pollFirst()); 
                } else {
                    syncOrder(maker, gwSeq);
                    break;
                }
            }
            if (makers != null && makers.isEmpty()) {
                counterSide.remove(bestPrice);
                makers.clear(); // 清理引用，防止記憶體洩漏
                dequePool.addLast(makers); 
            } else break;
        }
        if (taker.getQty() > taker.getFilled()) add(taker);
    }

    private void add(Order order) {
        TreeMap<Long, Deque<Order>> levels = (order.getSide() == OrderSide.BUY) ? bids : asks;
        levels.computeIfAbsent(order.getPrice(), k -> {
            Deque<Order> poolDeque = dequePool.pollFirst();
            return (poolDeque == null) ? new ArrayDeque<>(INITIAL_DEQUE_CAPACITY) : poolDeque;
        }).addLast(order);
        orderIndex.put(order.getOrderId(), order);
    }

    public void syncOrder(Order o, long gwSeq) {
        if (o.getFilled() == o.getQty()) o.setStatus((byte) 2);
        else if (o.getFilled() > 0) o.setStatus((byte) 1);
        o.setVersion(o.getVersion() + 1); o.setLastSeq(gwSeq);
        
        allOrdersDiskMap.put(o.getOrderId(), o);
        
        // --- 核心修正：正確維護活躍訂單 ID 清單 ---
        if (o.getStatus() < 2) {
            activeOrderIdDiskMap.put(o.getOrderId(), Boolean.TRUE);
        } else {
            activeOrderIdDiskMap.remove(o.getOrderId());
        }
    }

    public void remove(long orderId) { orderIndex.remove(orderId); }
}
