package open.vincentf13.service.spot.matching.engine;

import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.Trade;
import open.vincentf13.service.spot.sbe.OrderCreateDecoder;
import open.vincentf13.service.spot.sbe.Side;
import org.agrona.collections.Long2ObjectHashMap;
import java.util.*;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/** 
 內存訂單簿 (OrderBook)
 職責：領域狀態機，自主管理訂單 Admission、撮合流、最終狀態同步與生命週期池
 */
public class OrderBook {
    private static final int INITIAL_DEQUE_CAPACITY = 4096;
    private static final int POOL_MAX_SIZE = 100_000;

    private final int symbolId;
    private final ChronicleMap<Long, Order> allOrdersDiskMap = Storage.self().orders();
    private final ChronicleMap<Long, Boolean> activeOrderIdDiskMap = Storage.self().activeOrders();
    private final ChronicleMap<Long, Trade> tradeHistoryDiskMap = Storage.self().trades();

    private final TreeMap<Long, Deque<Order>> bids = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<Long, Deque<Order>> asks = new TreeMap<>();
    private final Long2ObjectHashMap<Order> orderIndex = new Long2ObjectHashMap<>(200_000, 0.5f); 

    /** 物件池：循環利用 Order 實體 */
    private final Deque<Order> orderPool = new ArrayDeque<>(POOL_MAX_SIZE);
    private final Trade reusableTrade = new Trade();

    @FunctionalInterface public interface TradeFinalizer {
        void onMatch(Order maker, long price, long qty);
    }

    public OrderBook(int symbolId) {
        this.symbolId = symbolId;
        // 初始預分配 1000 個，應對大多數交易對的啟動需求
        for (int i = 0; i < 1000; i++) orderPool.add(new Order());
    }

    /** 恢復模式專用：將磁碟數據掛回內存 */
    public void recoverOrder(Order o) {
        if (o == null || o.getStatus() >= 2) return;
        Order recovered = new Order();
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

    public void releaseOrder(Order o) { if (o != null) orderPool.addLast(o); }

    /** 
      處理 Taker 指令門面
      封裝了 Admission -> 撮合 -> 最終狀態同步 的完整生命週期
     */
    public Order processTaker(long orderId, OrderCreateDecoder sbe, long gwSeq, 
                             Supplier<Long> tradeIdSupplier, TradeFinalizer finalizer) {
        Order taker = borrowAndFill(orderId, sbe, gwSeq);
        match(taker, gwSeq, sbe.timestamp(), tradeIdSupplier, finalizer);
        
        // 關鍵：在此處執行 Taker 的結案同步
        syncOrder(taker, gwSeq);
        return taker;
    }

    private Order borrowAndFill(long orderId, OrderCreateDecoder sbe, long gwSeq) {
        Order o = orderPool.pollFirst();
        if (o == null) o = new Order();
        o.setOrderId(orderId); o.setUserId(sbe.userId()); o.setSymbolId(sbe.symbolId());
        o.setPrice(sbe.price()); o.setQty(sbe.qty()); o.setFilled(0);
        o.setSide((byte)(sbe.side() == Side.BUY ? 0 : 1)); o.setStatus((byte)0);
        o.setVersion(1); o.setLastSeq(gwSeq);
        o.setClientOrderId(sbe.clientOrderId());
        return o;
    }

    public static void rebuildAll(IntFunction<OrderBook> bookFinder) {
        Storage.self().activeOrders().forEach((id, active) -> {
            Order o = Storage.self().orders().getUsing(id, new Order());
            if (o != null && o.getStatus() < 2) bookFinder.apply(o.getSymbolId()).recoverOrder(o);
        });
    }

    private void match(Order taker, long gwSeq, long timestamp, Supplier<Long> tradeIdSupplier, TradeFinalizer finalizer) {
        final boolean isBuy = taker.getSide() == 0;
        final TreeMap<Long, Deque<Order>> counterSide = isBuy ? asks : bids;
        final long takerPrice = taker.getPrice();

        while (taker.getQty() > taker.getFilled()) {
            Map.Entry<Long, Deque<Order>> bestLevelEntry = counterSide.firstEntry();
            if (bestLevelEntry == null) break;

            final Deque<Order> makers = bestLevelEntry.getValue();
            final long bestPrice = bestLevelEntry.getKey();
            if (isBuy ? (takerPrice < bestPrice) : (takerPrice > bestPrice)) break;

            while (!makers.isEmpty() && taker.getQty() > taker.getFilled()) {
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
                    syncOrder(maker, gwSeq); // Maker 結案
                    orderIndex.remove(maker.getOrderId());
                    releaseOrder(makers.pollFirst()); 
                } else {
                    syncOrder(maker, gwSeq); // Maker 部分成交同步
                    break;
                }
            }
            if (makers.isEmpty()) counterSide.pollFirstEntry();
            else break;
        }
        if (taker.getQty() > taker.getFilled()) add(taker);
    }

    private void add(Order order) {
        TreeMap<Long, Deque<Order>> levels = (order.getSide() == 0) ? bids : asks;
        levels.computeIfAbsent(order.getPrice(), k -> new ArrayDeque<>(INITIAL_DEQUE_CAPACITY)).addLast(order);
        orderIndex.put(order.getOrderId(), order);
    }

    /** 
      狀態同步：修正了活躍狀態移除邏輯，防止 Map 洩漏
     */
    public void syncOrder(Order o, long gwSeq) {
        if (o.getFilled() == o.getQty()) o.setStatus((byte) 2);
        else if (o.getFilled() > 0) o.setStatus((byte) 1);
        o.setVersion(o.getVersion() + 1); o.setLastSeq(gwSeq);
        
        allOrdersDiskMap.put(o.getOrderId(), o);
        // 修正點：若訂單已結案 (Status=2)，必須從活躍 ID 映射中徹底移除
        if (o.getStatus() < 2) activeOrderIdDiskMap.put(o.getOrderId(), true);
        else activeOrderIdDiskMap.remove(o.getOrderId());
    }

    public void remove(long orderId) { orderIndex.remove(orderId); }
}
