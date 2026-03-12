package open.vincentf13.service.spot.matching.engine;

import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.sbe.SbeCodec;
import open.vincentf13.service.spot.infra.util.DecimalUtil;
import open.vincentf13.service.spot.model.CidKey;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.Trade;
import open.vincentf13.service.spot.sbe.OrderCreateDecoder;
import open.vincentf13.service.spot.sbe.OrderStatus;
import open.vincentf13.service.spot.sbe.Side;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

import static open.vincentf13.service.spot.infra.Constants.Asset;
import static open.vincentf13.service.spot.infra.Constants.PLATFORM_USER_ID;

/** 
 訂單處理核心邏輯 (Order Processor)
 職責：管理訂單簿狀態、執行撮合算法、協調帳務更新與持久化
 */
@Slf4j
@Component
public class OrderProcessor {
    private final Ledger ledger;
    private final ExecutionReporter reporter;
    
    private final Int2ObjectHashMap<OrderBook> books = new Int2ObjectHashMap<>();
    private final Long2ObjectHashMap<Order> activeOrderIndex = new Long2ObjectHashMap<>();
    
    // 內部重用的解碼器
    private final OrderCreateDecoder createDecoder = new OrderCreateDecoder();

    public OrderProcessor(Ledger ledger, ExecutionReporter reporter) {
        this.ledger = ledger;
        this.reporter = reporter;
    }

    /** 
      處理訂單創建指令 (入口)
      包含：解碼、冪等檢查、ID 生成、業務執行
     */
    public void processCreateCommand(UnsafeBuffer payload, long gwSeq, boolean isReplaying, Supplier<Long> orderIdSupplier, Supplier<Long> tradeIdSupplier) {
        SbeCodec.decode(payload, 0, createDecoder);
        String cid = createDecoder.clientOrderId();
        CidKey key = new CidKey(createDecoder.userId(), cid);
        
        // 1. 冪等性校驗
        Long resId = Storage.self().cids().get(key);
        if (resId != null) {
            if (!isReplaying) {
                Order o = Storage.self().orders().get(resId);
                if (o != null) reporter.resendReport(o, gwSeq);
            }
            return;
        }
        
        // 2. 獲取新訂單 ID
        long orderId = orderIdSupplier.get();
        
        // 3. 執行核心下單業務
        handleOrderCreate(createDecoder, gwSeq, orderId, cid, isReplaying, tradeIdSupplier);
        
        // 4. 記錄冪等索引
        Storage.self().cids().put(key, orderId);
    }

    private void handleOrderCreate(OrderCreateDecoder sbe, long gwSeq, long orderId, String cid, boolean isReplaying, Supplier<Long> tradeIdSupplier) {
        long ts = sbe.timestamp();
        boolean isBuy = sbe.side() == Side.BUY;
        long cost = isBuy ? DecimalUtil.mulCeil(sbe.price(), sbe.qty()) : sbe.qty();
        int aid = isBuy ? Asset.USDT : Asset.BTC;

        if (!ledger.tryFreeze(sbe.userId(), aid, gwSeq, cost)) {
            reporter.sendReport(sbe.userId(), 0, cid, OrderStatus.REJECTED, 0, 0, 0, 0, ts, gwSeq, isReplaying);
            return;
        }

        Order taker = new Order();
        taker.setOrderId(orderId); taker.setClientOrderId(cid); taker.setUserId(sbe.userId());
        taker.setSymbolId((int)sbe.symbolId()); taker.setPrice(sbe.price()); taker.setQty(sbe.qty());
        taker.setSide((byte)(isBuy ? 0 : 1)); taker.setStatus((byte)0);
        taker.setVersion(1); taker.setLastSeq(gwSeq);

        activeOrderIndex.put(orderId, taker); 
        persistOrder(taker);

        List<OrderBook.TradeEvent> trades = books.computeIfAbsent(taker.getSymbolId(), OrderBook::new).match(taker);
        for (OrderBook.TradeEvent t : trades) {
            long tid = tradeIdSupplier.get();
            persistTrade(t, tid, ts, gwSeq);
            processTradeLedger(t, gwSeq, taker);
            syncOrder(t.makerOrderId, gwSeq);
            reporter.sendReport(t.makerUserId, t.makerOrderId, "", OrderStatus.PARTIALLY_FILLED, t.price, t.qty, 0, 0, ts, gwSeq, isReplaying);
        }

        syncOrder(orderId, gwSeq);
        reporter.sendReport(taker.getUserId(), orderId, cid, taker.getStatus() == 2 ? OrderStatus.FILLED : OrderStatus.NEW, 0, 0, taker.getFilled(), 0, ts, gwSeq, isReplaying);
    }

    private void persistTrade(OrderBook.TradeEvent t, long tid, long ts, long gwSeq) {
        Trade r = new Trade();
        r.setTradeId(tid); r.setOrderId(t.makerOrderId); r.setPrice(t.price); r.setQty(t.qty); r.setTime(ts); r.setLastSeq(gwSeq);
        Storage.self().trades().put(tid, r);
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
            ledger.tradeSettleWithRefund(t.makerUserId, Asset.USDT, ceil, m != null ? DecimalUtil.mulCeil(m.getPrice(), t.qty) : ceil, Asset.BTC, t.qty, gwSeq);
        }
        if (ceil > floor) ledger.addAvailable(PLATFORM_USER_ID, Asset.USDT, gwSeq, ceil - floor);
    }

    public void syncOrder(long id, long gwSeq) {
        Order o = activeOrderIndex.get(id);
        if (o != null) {
            if (o.getFilled() == o.getQty()) { o.setStatus((byte)2); activeOrderIndex.remove(id); }
            else if (o.getFilled() > 0) o.setStatus((byte)1);
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

    public void rebuildIndex(Order o) {
        books.computeIfAbsent(o.getSymbolId(), OrderBook::new).add(o);
        activeOrderIndex.put(o.getOrderId(), o);
    }
}
