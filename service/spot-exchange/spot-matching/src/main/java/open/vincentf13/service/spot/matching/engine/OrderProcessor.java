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

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 訂單處理核心邏輯 (Order Processor)
 職責：管理訂單簿狀態、執行撮合算法、協調帳務更新與持久化
 */
@Slf4j
@Component
public class OrderProcessor {
    private final Ledger ledger;
    private final ExecutionReporter reporter;
    
    // 內存狀態：訂單簿與活躍索引
    private final Int2ObjectHashMap<OrderBook> books = new Int2ObjectHashMap<>();
    private final Long2ObjectHashMap<Order> activeOrderIndex = new Long2ObjectHashMap<>();

    public OrderProcessor(Ledger ledger, ExecutionReporter reporter) {
        this.ledger = ledger;
        this.reporter = reporter;
    }

    /** 
      處理限價單創建
     */
    public long handleOrderCreate(OrderCreateDecoder sbe, long gwSeq, long orderId, String cid, boolean isReplaying) {
        long ts = sbe.timestamp();
        long cost = (sbe.side() == Side.BUY) ? DecimalUtil.mulCeil(sbe.price(), sbe.qty()) : sbe.qty();
        int aid = (sbe.side() == Side.BUY) ? Asset.USDT : Asset.BTC;

        // 1. 風控凍結
        if (!ledger.tryFreeze(sbe.userId(), aid, gwSeq, cost)) {
            reporter.sendReport(sbe.userId(), 0, cid, OrderStatus.REJECTED, 0, 0, 0, 0, ts, gwSeq, isReplaying);
            return ID_REJECTED;
        }

        // 2. 對象初始化
        Order o = new Order();
        o.setOrderId(orderId); o.setClientOrderId(cid); o.setUserId(sbe.userId());
        o.setSymbolId((int)sbe.symbolId()); o.setPrice(sbe.price()); o.setQty(sbe.qty());
        o.setSide((byte)(sbe.side() == Side.BUY ? 0 : 1)); o.setStatus((byte)0);
        o.setVersion(1); o.setLastSeq(gwSeq);

        activeOrderIndex.put(orderId, o); 
        persistOrder(o);

        // 3. 撮合執行
        List<OrderBook.TradeEvent> trades = books.computeIfAbsent(o.getSymbolId(), OrderBook::new).match(o);
        
        // 4. 成交處理
        for (OrderBook.TradeEvent t : trades) {
            processTrade(t, ts, gwSeq, o, isReplaying);
        }

        // 5. 最終狀態同步
        syncOrder(orderId, gwSeq);
        OrderStatus st = (o.getStatus() == 2) ? OrderStatus.FILLED : OrderStatus.NEW;
        reporter.sendReport(sbe.userId(), orderId, cid, st, 0, 0, o.getFilled(), 0, ts, gwSeq, isReplaying);
        
        return orderId;
    }

    private void processTrade(OrderBook.TradeEvent t, long ts, long gwSeq, Order taker, boolean isReplaying) {
        // 持久化成交紀錄
        Trade r = new Trade();
        r.setTradeId(System.nanoTime()); // 實際應由 Progress 生成
        r.setOrderId(t.makerOrderId); r.setPrice(t.price); r.setQty(t.qty); r.setTime(ts); r.setLastSeq(gwSeq);
        Storage.self().trades().put(r.getTradeId(), r);

        // 帳務結算
        processTradeLedger(t, gwSeq, taker);
        
        // 更新 Maker 狀態
        syncOrder(t.makerOrderId, gwSeq);
        
        // 發送 Maker 回報
        reporter.sendReport(t.makerUserId, t.makerOrderId, "", OrderStatus.PARTIALLY_FILLED, t.price, t.qty, 0, 0, ts, gwSeq, isReplaying);
    }

    private void processTradeLedger(OrderBook.TradeEvent t, long gwSeq, Order taker) {
        long floor = DecimalUtil.mulFloor(t.price, t.qty);
        long ceil = DecimalUtil.mulCeil(t.price, t.qty);
        if (taker.getSide() == 0) {
            ledger.tradeSettleWithRefund(t.takerUserId, Asset.USDT, ceil, DecimalUtil.mulCeil(taker.getPrice(), t.qty), Asset.BTC, t.qty, gwSeq);
            ledger.tradeSettle(t.makerUserId, Asset.BTC, t.qty, Asset.USDT, floor, gwSeq);
        } else {
            ledger.tradeSettle(t.takerUserId, Asset.BTC, t.qty, Asset.USDT, floor, gwSeq);
            Order m = activeOrderIndex.get(t.makerOrderId);
            long mCeil = (m != null) ? DecimalUtil.mulCeil(m.getPrice(), t.qty) : ceil;
            ledger.tradeSettleWithRefund(t.makerUserId, Asset.USDT, ceil, mCeil, Asset.BTC, t.qty, gwSeq);
        }
        if (ceil > floor) ledger.addAvailable(PLATFORM_USER_ID, Asset.USDT, gwSeq, ceil - floor);
    }

    public void syncOrder(long id, long gwSeq) {
        Order o = activeOrderIndex.get(id);
        if (o != null) {
            if (o.getFilled() == o.getQty()) { o.setStatus((byte)2); activeOrderIndex.remove(id); }
            o.setVersion(o.getVersion() + 1); o.setLastSeq(gwSeq); 
            persistOrder(o);
        }
    }

    public void persistOrder(Order o) {
        Order ex = Storage.self().orders().get(o.getOrderId());
        if (ex == null || ex.getLastSeq() < o.getLastSeq()) {
            Storage.self().orders().put(o.getOrderId(), o);
            if (o.getStatus() < 2) Storage.self().activeOrders().put(o.getOrderId(), true);
            else Storage.self().activeOrders().remove(o.getOrderId());
        }
    }

    /** 重建索引 (用於啟動恢復) */
    public void rebuildIndex(Order o) {
        books.computeIfAbsent(o.getSymbolId(), OrderBook::new).add(o);
        activeOrderIndex.put(o.getOrderId(), o);
    }

    public Order getActiveOrder(long id) { return activeOrderIndex.get(id); }
}
