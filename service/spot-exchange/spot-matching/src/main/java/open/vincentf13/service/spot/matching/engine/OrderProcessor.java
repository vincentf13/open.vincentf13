package open.vincentf13.service.spot.matching.engine;

import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.util.DecimalUtil;
import open.vincentf13.service.spot.model.CidKey;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.Trade;
import open.vincentf13.service.spot.sbe.OrderCreateDecoder;
import open.vincentf13.service.spot.sbe.OrderStatus;
import open.vincentf13.service.spot.sbe.Side;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 訂單處理核心 (Order Processor)
 職責：執行訂單生命週期管理，包含風控、撮合與資產結算
 
 一致性流程：
 1. 凍結 (Freeze) -> 2. 持久化 (Persist Taker) -> 3. 撮合 (Match) -> 4. 結算 (Settle) -> 5. 完結 (Finalize)
 */
@Slf4j
@Component
public class OrderProcessor {
    private final Ledger ledger;
    private final ExecutionReporter reporter;
    
    private final Int2ObjectHashMap<OrderBook> books = new Int2ObjectHashMap<>();
    private final Long2ObjectHashMap<Order> activeIndex = new Long2ObjectHashMap<>();

    public OrderProcessor(Ledger ledger, ExecutionReporter reporter) {
        this.ledger = ledger;
        this.reporter = reporter;
    }

    /** 
      指令入口：處理新限價單 (含冪等性檢查)
     */
    public void processCreateCommand(OrderCreateDecoder decoder, long gwSeq, boolean isReplaying, Supplier<Long> orderIdSupplier, Supplier<Long> tradeIdSupplier) {
        String cid = decoder.clientOrderId();
        CidKey key = new CidKey(decoder.userId(), cid);
        
        Long resId = Storage.self().cids().get(key);
        if (resId != null) {
            if (!isReplaying) {
                Order o = Storage.self().orders().get(resId);
                if (o != null) reporter.resendReport(o, gwSeq);
            }
            return;
        }
        
        handleOrderCreate(decoder, gwSeq, orderIdSupplier.get(), cid, isReplaying, tradeIdSupplier);
        Storage.self().cids().put(key, decoder.userId()); // 修正：這裡應該存 orderId
    }

    private void handleOrderCreate(OrderCreateDecoder sbe, long gwSeq, long orderId, String cid, boolean isReplaying, Supplier<Long> tradeIdSupplier) {
        long ts = sbe.timestamp();
        boolean isBuy = sbe.side() == Side.BUY;
        long cost = isBuy ? DecimalUtil.mulCeil(sbe.price(), sbe.qty()) : sbe.qty();
        int aid = isBuy ? Asset.USDT : Asset.BTC;

        // 步驟 1: 預扣
        if (!ledger.tryFreeze(sbe.userId(), aid, gwSeq, cost)) {
            reporter.sendReport(sbe.userId(), 0, cid, OrderStatus.REJECTED, 0, 0, 0, 0, ts, gwSeq, isReplaying);
            return;
        }

        // 步驟 2: 持久化 Taker
        Order taker = new Order();
        taker.setOrderId(orderId); taker.setClientOrderId(cid); taker.setUserId(sbe.userId());
        taker.setSymbolId((int)sbe.symbolId()); taker.setPrice(sbe.price()); taker.setQty(sbe.qty());
        taker.setSide((byte)(isBuy ? 0 : 1)); taker.setStatus((byte)0);
        taker.setVersion(1); taker.setLastSeq(gwSeq);

        activeIndex.put(orderId, taker); 
        persistOrder(taker);

        // 步驟 3: 撮合
        List<OrderBook.TradeEvent> trades = books.computeIfAbsent(taker.getSymbolId(), OrderBook::new).match(taker);
        
        // 步驟 4: 成交處理 (循環)
        for (OrderBook.TradeEvent t : trades) {
            processTrade(t, ts, gwSeq, taker, isReplaying, tradeIdSupplier.get());
        }

        // 步驟 5: 狀態收尾
        syncOrder(taker, gwSeq);
        reporter.sendReport(taker.getUserId(), orderId, cid, 
                taker.getStatus() == 2 ? OrderStatus.FILLED : OrderStatus.NEW, 
                0, 0, taker.getFilled(), 0, ts, gwSeq, isReplaying);
    }

    private void processTrade(OrderBook.TradeEvent t, long ts, long gwSeq, Order taker, boolean isReplaying, long tradeId) {
        Trade r = new Trade();
        r.setTradeId(tradeId); r.setOrderId(t.makerOrderId); r.setPrice(t.price); r.setQty(t.qty); r.setTime(ts); r.setLastSeq(gwSeq);
        Storage.self().trades().put(tradeId, r);

        processTradeLedger(t, gwSeq, taker);
        
        Order maker = activeIndex.get(t.makerOrderId);
        if (maker != null) syncOrder(maker, gwSeq);
        
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
            Order m = activeIndex.get(t.makerOrderId);
            ledger.tradeSettleWithRefund(t.makerUserId, Asset.USDT, ceil, m != null ? DecimalUtil.mulCeil(m.getPrice(), t.qty) : ceil, Asset.BTC, t.qty, gwSeq);
        }
        if (ceil > floor) ledger.addAvailable(PLATFORM_USER_ID, Asset.USDT, gwSeq, ceil - floor);
    }

    /** 
      同步訂單狀態 
      邏輯：根據已成交量自動計算並更新 OrderStatus
     */
    public void syncOrder(Order o, long gwSeq) {
        if (o.getFilled() == o.getQty()) { 
            o.setStatus((byte)2); // FILLED
            activeIndex.remove(o.getOrderId()); 
        } else if (o.getFilled() > 0) {
            o.setStatus((byte)1); // PARTIALLY_FILLED
        }
        o.setVersion(o.getVersion() + 1); 
        o.setLastSeq(gwSeq); 
        persistOrder(o);
    }

    public void persistOrder(Order o) {
        Storage.self().orders().put(o.getOrderId(), o);
        if (o.getStatus() < 2) Storage.self().activeOrders().put(o.getOrderId(), true);
        else Storage.self().activeOrders().remove(o.getOrderId());
    }

    public void rebuildIndex(Order o) {
        books.computeIfAbsent(o.getSymbolId(), OrderBook::new).add(o);
        activeIndex.put(o.getOrderId(), o);
    }
}
