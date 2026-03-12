package open.vincentf13.service.spot.matching.engine;

import open.vincentf13.service.spot.model.Order;
import org.agrona.collections.Long2ObjectHashMap;

import java.util.*;

/**
 內存訂單簿 (OrderBook) - 性能預分配增強版
 
 優化點：
 1. 擴大初始容量：INITIAL_DEQUE_CAPACITY 設為 4096，減少高深度價位的擴容頻率。
 2. 緩存友善：4096 個指標約佔 32KB，完美契合現代 CPU L1 Cache 大小，最大化遍歷速度。
 */
public class OrderBook {
    // --- 預分配空間優化 ---
    /**
     每個價格位準預計掛單數：4096 確保主流交易對在熱點價位無需頻繁擴容
     */
    private static final int INITIAL_DEQUE_CAPACITY = 4096;
    /**
     單個 Symbol 全局掛單數預估：提高至 200,000 以應對高併發盤口
     */
    private static final int INITIAL_ORDER_MAP_CAPACITY = 200_000;
    private static final float LOAD_FACTOR = 0.5f;
    
    private final int symbolId;
    private final TreeMap<Long, Deque<Order>> bids = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<Long, Deque<Order>> asks = new TreeMap<>();
    private final Long2ObjectHashMap<Order> orderIndex = new Long2ObjectHashMap<>(INITIAL_ORDER_MAP_CAPACITY, LOAD_FACTOR);
    
    public OrderBook(int symbolId) {
        this.symbolId = symbolId;
    }
    
    /**
     執行撮合 (回調模式)
     
     優化設計：採用 Functional Callback 代替返回 List<TradeEvent>
     1. Zero-Allocation：徹底消除每秒數萬次撮合產生的 ArrayList 與 Event 物件分配，壓制 Young GC。
     2. Cache Locality：在數據尚在 L1/L2 緩存時立即觸發結算邏輯，實現流水線式處理，極大降低延遲。
     */
    public void match(Order taker,
                      TradeHandler handler) {
        final boolean isBuy = taker.getSide() == 0;
        final TreeMap<Long, Deque<Order>> counterSide = isBuy ? asks : bids;
        final long takerPrice = taker.getPrice();
        
        while (taker.getQty() > taker.getFilled()) {
            Map.Entry<Long, Deque<Order>> bestLevelEntry = counterSide.firstEntry();
            if (bestLevelEntry == null)
                break;
            
            final long bestPrice = bestLevelEntry.getKey();
            if (isBuy ? (takerPrice < bestPrice) : (takerPrice > bestPrice))
                break;
            
            final Deque<Order> makers = bestLevelEntry.getValue();
            
            while (!makers.isEmpty() && taker.getQty() > taker.getFilled()) {
                Order maker = makers.peekFirst();
                
                if (!orderIndex.containsKey(maker.getOrderId())) {
                    makers.pollFirst();
                    continue;
                }
                
                long matchQty = Math.min(taker.getQty() - taker.getFilled(), maker.getQty() - maker.getFilled());
                handler.onTrade(maker.getUserId(), taker.getUserId(), bestPrice, matchQty, maker.getOrderId());
                
                taker.setFilled(taker.getFilled() + matchQty);
                maker.setFilled(maker.getFilled() + matchQty);
                
                if (maker.getFilled() == maker.getQty()) {
                    makers.pollFirst();
                    orderIndex.remove(maker.getOrderId());
                }
            }
            
            if (makers.isEmpty())
                counterSide.pollFirstEntry();
            else
                break;
        }
        
        if (taker.getQty() > taker.getFilled())
            add(taker);
    }
    
    public void add(Order order) {
        TreeMap<Long, Deque<Order>> levels = (order.getSide() == 0) ? bids : asks;
        levels.computeIfAbsent(order.getPrice(), k -> new ArrayDeque<>(INITIAL_DEQUE_CAPACITY)).addLast(order);
        orderIndex.put(order.getOrderId(), order);
    }
    
    public void remove(long orderId) {
        orderIndex.remove(orderId);
    }
    
    @FunctionalInterface
    public interface TradeHandler {
        void onTrade(long makerUserId,
                     long takerUserId,
                     long price,
                     long qty,
                     long makerOrderId);
    }
}
