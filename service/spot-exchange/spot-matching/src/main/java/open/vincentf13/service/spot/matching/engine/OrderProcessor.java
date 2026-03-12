package open.vincentf13.service.spot.matching.engine;

import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.sbe.SbeCodec;
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
 */
@Slf4j
@Component
public class OrderProcessor {
    // --- 持久化數據層 (Source of Truth - 落地磁碟) ---
    
    /** 訂單全集磁碟表：存儲系統歷史上所有的訂單詳細快照 */
    private final ChronicleMap<Long, Order> allOrdersDiskMap = Storage.self().orders();
    
    /** 活躍訂單 ID 磁碟表：僅存儲當前簿中活躍訂單的 ID，用於啟動時快速重建內存狀態 */
    private final ChronicleMap<Long, Boolean> activeOrderIdDiskMap = Storage.self().activeOrders();
    
    /** 成交歷史磁碟表：持久化記錄所有的撮合成交流水，供審計與 Query 模組使用 */
    private final ChronicleMap<Long, Trade> tradeHistoryDiskMap = Storage.self().trades();
    
    /** 冪等性映射磁碟表：ClientOrderId -> OrderId 映射，確保同一個請求不會被重複處理 */
    private final ChronicleMap<CidKey, Long> clientOrderIdDiskMap = Storage.self().cids();

    // --- 撮合加速層 (High-speed View - 純內存) ---
    
    /** Symbol 訂單簿映射：按交易對 (SymbolId) 組織的內存價格優先隊列 */
    private final Int2ObjectHashMap<OrderBook> symbolOrderBookMap = new Int2ObjectHashMap<>();
    
    /** 活躍訂單內存索引：內存中活躍訂單對象的直接指針，實現微秒級的成交狀態更新，避免磁碟 IO */
    private final Long2ObjectHashMap<Order> activeOrderMemoryIndex = new Long2ObjectHashMap<>();
    
    private final Ledger ledger;
    private final ExecutionReporter reporter;
    private final OrderCreateDecoder decoder = new OrderCreateDecoder();

    public OrderProcessor(Ledger ledger, ExecutionReporter reporter) {
        this.ledger = ledger;
        this.reporter = reporter;
    }

    /** 
      重建狀態：從持久化數據恢復內存加速視圖
     */
    public void rebuildState() {
        log.info("OrderProcessor 正在恢復內存索引...");
        activeOrderIdDiskMap.keySet().forEach(id -> {
            Order o = allOrdersDiskMap.get(id);
            if (o != null && o.getStatus() < 2) {
                symbolOrderBookMap.computeIfAbsent(o.getSymbolId(), OrderBook::new).add(o);
                activeOrderMemoryIndex.put(id, o);
            } else activeOrderIdDiskMap.remove(id);
        });
    }

    public void processCreateCommand(UnsafeBuffer payload, long gwSeq, Supplier<Long> orderIdSupplier, Supplier<Long> tradeIdSupplier) {
        SbeCodec.decode(payload, 0, decoder);
        String cid = decoder.clientOrderId();
        CidKey key = new CidKey(decoder.userId(), cid);
        
        Long existingOid = clientOrderIdDiskMap.get(key);
        if (existingOid != null) {
            Order o = allOrdersDiskMap.get(existingOid);
            if (o != null) reporter.resendReport(o, gwSeq);
            return;
        }
        
        handleOrderCreate(decoder, gwSeq, orderIdSupplier.get(), cid, tradeIdSupplier);
    }

    private void handleOrderCreate(OrderCreateDecoder sbe, long gwSeq, long orderId, String cid, Supplier<Long> tradeIdSupplier) {
        long ts = sbe.timestamp();
        boolean isBuy = sbe.side() == Side.BUY;
        long cost = isBuy ? DecimalUtil.mulCeil(sbe.price(), sbe.qty()) : sbe.qty();
        int aid = isBuy ? Asset.USDT : Asset.BTC;

        if (!ledger.tryFreeze(sbe.userId(), aid, gwSeq, cost)) {
            reporter.sendReport(sbe.userId(), 0, cid, OrderStatus.REJECTED, 0, 0, 0, 0, ts, gwSeq);
            return;
        }

        Order taker = new Order();
        taker.setOrderId(orderId); taker.setClientOrderId(cid); taker.setUserId(sbe.userId());
        taker.setSymbolId((int)sbe.symbolId()); taker.setPrice(sbe.price()); taker.setQty(sbe.qty());
        taker.setSide((byte)(isBuy ? 0 : 1)); taker.setStatus((byte)0);
        taker.setVersion(1); taker.setLastSeq(gwSeq);

        activeOrderMemoryIndex.put(orderId, taker); 
        persistOrder(taker);

        List<OrderBook.TradeEvent> matchEvents = symbolOrderBookMap.computeIfAbsent(taker.getSymbolId(), OrderBook::new).match(taker);
        for (OrderBook.TradeEvent t : matchEvents) {
            long tid = tradeIdSupplier.get();
            persistTrade(t, tid, ts, gwSeq);
            processTradeLedger(t, gwSeq, taker);
            Order maker = activeOrderMemoryIndex.get(t.makerOrderId);
            if (maker != null) syncOrder(maker, gwSeq);
            reporter.sendReport(t.makerUserId, t.makerOrderId, "", OrderStatus.PARTIALLY_FILLED, t.price, t.qty, 0, 0, ts, gwSeq);
        }

        syncOrder(taker, gwSeq);
        reporter.sendReport(taker.getUserId(), orderId, cid, 
                taker.getStatus() == 2 ? OrderStatus.FILLED : OrderStatus.NEW, 
                0, 0, taker.getFilled(), 0, ts, gwSeq);
        
        clientOrderIdDiskMap.put(new CidKey(taker.getUserId(), cid), orderId);
    }

    private void persistTrade(OrderBook.TradeEvent t, long tid, long ts, long gwSeq) {
        Trade r = new Trade();
        r.setTradeId(tid); r.setOrderId(t.makerOrderId); r.setPrice(t.price); r.setQty(t.qty); r.setTime(ts); r.setLastSeq(gwSeq);
        tradeHistoryDiskMap.put(tid, r);
    }

    private void processTradeLedger(OrderBook.TradeEvent t, long gwSeq, Order taker) {
        long floor = DecimalUtil.mulFloor(t.price, t.qty);
        long ceil = DecimalUtil.mulCeil(t.price, t.qty);
        if (taker.getSide() == 0) {
            ledger.tradeSettleWithRefund(t.takerUserId, Asset.USDT, ceil, DecimalUtil.mulCeil(taker.getPrice(), t.qty), Asset.BTC, t.qty, gwSeq);
            ledger.tradeSettle(t.makerUserId, Asset.BTC, t.qty, Asset.USDT, floor, gwSeq);
        } else {
            ledger.tradeSettle(t.takerUserId, Asset.BTC, t.qty, Asset.USDT, floor, gwSeq);
            Order m = activeOrderMemoryIndex.get(t.makerOrderId);
            ledger.tradeSettleWithRefund(t.makerUserId, Asset.USDT, ceil, m != null ? DecimalUtil.mulCeil(m.getPrice(), t.qty) : ceil, Asset.BTC, t.qty, gwSeq);
        }
        if (ceil > floor) ledger.addAvailable(PLATFORM_USER_ID, Asset.USDT, gwSeq, ceil - floor);
    }

    public void syncOrder(Order o, long gwSeq) {
        if (o.getFilled() == o.getQty()) { 
            o.setStatus((byte)2); activeOrderMemoryIndex.remove(o.getOrderId()); 
        } else if (o.getFilled() > 0) o.setStatus((byte)1);
        o.setVersion(o.getVersion() + 1); o.setLastSeq(gwSeq); 
        persistOrder(o);
    }

    public void persistOrder(Order o) {
        allOrdersDiskMap.put(o.getOrderId(), o);
        if (o.getStatus() < 2) activeOrderIdDiskMap.put(o.getOrderId(), true);
        else activeOrderIdDiskMap.remove(o.getOrderId());
    }
}
