package open.vincentf13.service.spot.matching.engine;

import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.util.DecimalUtil;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.Trade;
import open.vincentf13.service.spot.sbe.OrderCreateDecoder;
import open.vincentf13.service.spot.sbe.OrderStatus;
import open.vincentf13.service.spot.sbe.Side;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 訂單處理核心邏輯 (Order Processor)
 職責：管理訂單簿狀態、執行撮合算法、協調帳務更新與持久化
 
 業務流程與一致性保證：
 1. 資金預扣 (Ledger)：確保用戶有足夠資產，且透過 lastSeq 實現冪等性。
 2. 狀態持久化 (Order)：訂單進入撮合前先本地存檔，保證崩潰後可恢復。
 3. 撮合與結算：成交後同步更新買賣雙方帳務與訂單狀態，並記錄成交歷史。
 */
@Slf4j
@Component
public class OrderProcessor {
    private final Ledger ledger;
    private final ExecutionReporter reporter;
    
    // 內存狀態：訂單簿集合與活躍訂單索引
    private final Int2ObjectHashMap<OrderBook> books = new Int2ObjectHashMap<>();
    private final Long2ObjectHashMap<Order> activeOrderIndex = new Long2ObjectHashMap<>();

    public OrderProcessor(Ledger ledger, ExecutionReporter reporter) {
        this.ledger = ledger;
        this.reporter = reporter;
    }

    /** 
      處理限價單創建
      @param sbe 指令數據解碼器
      @param gwSeq Gateway 原始序號
      @param orderId 由 Engine 分配的全局唯一 ID
      @param cid 用戶自定義訂單 ID
      @param isReplaying 重播模式旗標
      @param tradeIdSupplier 確定性的成交 ID 生成器
     */
    public long handleOrderCreate(OrderCreateDecoder sbe, long gwSeq, long orderId, String cid, boolean isReplaying, Supplier<Long> tradeIdSupplier) {
        long ts = sbe.timestamp();
        boolean isBuy = sbe.side() == Side.BUY;
        // 買單凍結金額（限定價 * 數量），賣單凍結資產數量
        long cost = isBuy ? DecimalUtil.mulCeil(sbe.price(), sbe.qty()) : sbe.qty();
        int aid = isBuy ? Asset.USDT : Asset.BTC;

        // 1. 資產檢查與凍結 (帳務一致性起點)
        if (!ledger.tryFreeze(sbe.userId(), aid, gwSeq, cost)) {
            reporter.sendReport(sbe.userId(), 0, cid, OrderStatus.REJECTED, 0, 0, 0, 0, ts, gwSeq, isReplaying);
            return ID_REJECTED;
        }

        // 2. 初始化並持久化 Taker 訂單對象
        Order taker = new Order();
        taker.setOrderId(orderId); taker.setClientOrderId(cid); taker.setUserId(sbe.userId());
        taker.setSymbolId((int)sbe.symbolId()); taker.setPrice(sbe.price()); taker.setQty(sbe.qty());
        taker.setSide((byte)(isBuy ? 0 : 1)); taker.setStatus((byte)0);
        taker.setVersion(1); taker.setLastSeq(gwSeq);

        activeOrderIndex.put(orderId, taker); 
        persistOrder(taker);

        // 3. 進入訂單簿執行撮合
        List<OrderBook.TradeEvent> trades = books.computeIfAbsent(taker.getSymbolId(), OrderBook::new).match(taker);
        
        // 4. 循環處理每一筆成交
        for (OrderBook.TradeEvent t : trades) {
            long tid = tradeIdSupplier.get();
            // A. 持久化成交紀錄 (審計一致性)
            persistTrade(t, tid, ts, gwSeq);
            // B. 執行帳務結算 (資產一致性)
            processTradeLedger(t, gwSeq, taker);
            // C. 更新 Maker 訂單狀態
            syncOrder(t.makerOrderId, gwSeq);
            // D. 發送 Maker 的成交回報
            reporter.sendReport(t.makerUserId, t.makerOrderId, "", OrderStatus.PARTIALLY_FILLED, t.price, t.qty, 0, 0, ts, gwSeq, isReplaying);
        }

        // 5. 更新 Taker 訂單最終狀態並發送 Taker 回報
        syncOrder(orderId, gwSeq);
        OrderStatus finalStatus = taker.getStatus() == 2 ? OrderStatus.FILLED : OrderStatus.NEW;
        reporter.sendReport(taker.getUserId(), orderId, cid, finalStatus, 0, 0, taker.getFilled(), 0, ts, gwSeq, isReplaying);
        
        return orderId;
    }

    /** 持久化成交紀錄至本地存儲 */
    private void persistTrade(OrderBook.TradeEvent t, long tid, long ts, long gwSeq) {
        Trade r = new Trade();
        r.setTradeId(tid); r.setOrderId(t.makerOrderId); r.setPrice(t.price); r.setQty(t.qty); r.setTime(ts); r.setLastSeq(gwSeq);
        Storage.self().trades().put(tid, r);
    }

    /** 成交後的實時帳務結算 */
    private void processTradeLedger(OrderBook.TradeEvent t, long gwSeq, Order taker) {
        long floor = DecimalUtil.mulFloor(t.price, t.qty);
        long ceil = DecimalUtil.mulCeil(t.price, t.qty);
        
        if (taker.getSide() == 0) { // Taker 買入
            // Taker 結算並獲得可能的溢價退款（因為成交價低於限定價）
            ledger.tradeSettleWithRefund(t.takerUserId, Asset.USDT, ceil, DecimalUtil.mulCeil(taker.getPrice(), t.qty), Asset.BTC, t.qty, gwSeq);
            // Maker 賣出獲得資產
            ledger.tradeSettle(t.makerUserId, Asset.BTC, t.qty, Asset.USDT, floor, gwSeq);
        } else { // Taker 賣出
            // Taker 獲得資產
            ledger.tradeSettle(t.takerUserId, Asset.BTC, t.qty, Asset.USDT, floor, gwSeq);
            // Maker 結算並獲得可能的溢價退款
            Order m = activeOrderIndex.get(t.makerOrderId);
            ledger.tradeSettleWithRefund(t.makerUserId, Asset.USDT, ceil, m != null ? DecimalUtil.mulCeil(m.getPrice(), t.qty) : ceil, Asset.BTC, t.qty, gwSeq);
        }
        
        // 將捨入誤差利潤歸入平台
        if (ceil > floor) ledger.addAvailable(PLATFORM_USER_ID, Asset.USDT, gwSeq, ceil - floor);
    }

    /** 同步訂單內存狀態與持久化快照 */
    public void syncOrder(long id, long gwSeq) {
        Order o = activeOrderIndex.get(id);
        if (o != null) {
            if (o.getFilled() == o.getQty()) { 
                o.setStatus((byte)2); 
                activeOrderIndex.remove(id); 
            } else if (o.getFilled() > 0) {
                o.setStatus((byte)1); // 部分成交
            }
            o.setVersion(o.getVersion() + 1); 
            o.setLastSeq(gwSeq); 
            persistOrder(o);
        }
    }

    /** 持久化訂單對象，執行寫入前進行 Sequence 校驗防禦 */
    public void persistOrder(Order o) {
        Order ex = Storage.self().orders().get(o.getOrderId());
        if (ex == null || ex.getLastSeq() < o.getLastSeq()) {
            Storage.self().orders().put(o.getOrderId(), o);
            // 同步活躍索引狀態
            if (o.getStatus() < 2) Storage.self().activeOrders().put(o.getOrderId(), true);
            else Storage.self().activeOrders().remove(o.getOrderId());
        }
    }

    /** 啟動恢復專用：重建內存索引 */
    public void rebuildIndex(Order o) {
        books.computeIfAbsent(o.getSymbolId(), OrderBook::new).add(o);
        activeOrderIndex.put(o.getOrderId(), o);
    }
}
