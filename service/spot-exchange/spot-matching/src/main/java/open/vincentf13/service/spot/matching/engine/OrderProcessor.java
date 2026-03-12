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
 職責：純粹的業務邏輯執行，不維護重播狀態（由 Reporter 統一攔截輸出）
 */
@Slf4j
@Component
public class OrderProcessor {
    private final ChronicleMap<Long, Order> orders = Storage.self().orders();
    private final ChronicleMap<Long, Boolean> activeOrders = Storage.self().activeOrders();
    private final ChronicleMap<Long, Trade> tradesStore = Storage.self().trades();
    private final ChronicleMap<CidKey, Long> cids = Storage.self().cids();

    private final Ledger ledger;
    private final ExecutionReporter reporter;
    
    private final Int2ObjectHashMap<OrderBook> books = new Int2ObjectHashMap<>();
    private final Long2ObjectHashMap<Order> activeIndex = new Long2ObjectHashMap<>();
    private final OrderCreateDecoder decoder = new OrderCreateDecoder();

    public OrderProcessor(Ledger ledger, ExecutionReporter reporter) {
        this.ledger = ledger;
        this.reporter = reporter;
    }

    public void rebuildState() {
        log.info("OrderProcessor 正在恢復內存索引...");
        activeOrders.keySet().forEach(id -> {
            Order o = orders.get(id);
            if (o != null && o.getStatus() < 2) {
                books.computeIfAbsent(o.getSymbolId(), OrderBook::new).add(o);
                activeIndex.put(id, o);
            } else activeOrders.remove(id);
        });
    }

    public void processCreateCommand(UnsafeBuffer payload, long gwSeq, Supplier<Long> orderIdSupplier, Supplier<Long> tradeIdSupplier) {
        SbeCodec.decode(payload, 0, decoder);
        String cid = decoder.clientOrderId();
        CidKey key = new CidKey(decoder.userId(), cid);
        
        Long existingOid = cids.get(key);
        if (existingOid != null) {
            Order o = orders.get(existingOid);
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

        activeIndex.put(orderId, taker); 
        persistOrder(taker);

        List<OrderBook.TradeEvent> matchEvents = books.computeIfAbsent(taker.getSymbolId(), OrderBook::new).match(taker);
        for (OrderBook.TradeEvent t : matchEvents) {
            long tid = tradeIdSupplier.get();
            persistTrade(t, tid, ts, gwSeq);
            processTradeLedger(t, gwSeq, taker);
            Order maker = activeIndex.get(t.makerOrderId);
            if (maker != null) syncOrder(maker, gwSeq);
            reporter.sendReport(t.makerUserId, t.makerOrderId, "", OrderStatus.PARTIALLY_FILLED, t.price, t.qty, 0, 0, ts, gwSeq);
        }

        syncOrder(taker, gwSeq);
        reporter.sendReport(taker.getUserId(), orderId, cid, 
                taker.getStatus() == 2 ? OrderStatus.FILLED : OrderStatus.NEW, 
                0, 0, taker.getFilled(), 0, ts, gwSeq);
        
        cids.put(new CidKey(taker.getUserId(), cid), orderId);
    }

    private void persistTrade(OrderBook.TradeEvent t, long tid, long ts, long gwSeq) {
        Trade r = new Trade();
        r.setTradeId(tid); r.setOrderId(t.makerOrderId); r.setPrice(t.price); r.setQty(t.qty); r.setTime(ts); r.setLastSeq(gwSeq);
        tradesStore.put(tid, r);
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

    public void syncOrder(Order o, long gwSeq) {
        if (o.getFilled() == o.getQty()) { 
            o.setStatus((byte)2); activeIndex.remove(o.getOrderId()); 
        } else if (o.getFilled() > 0) o.setStatus((byte)1);
        o.setVersion(o.getVersion() + 1); o.setLastSeq(gwSeq); 
        persistOrder(o);
    }

    public void persistOrder(Order o) {
        orders.put(o.getOrderId(), o);
        if (o.getStatus() < 2) activeOrders.put(o.getOrderId(), true);
        else activeOrders.remove(o.getOrderId());
    }
}
