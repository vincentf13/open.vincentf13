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

import java.util.function.Supplier;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 訂單處理核心 (Order Processor) - Zero-GC 優化版
 職責：管理訂單生命週期，實現物件復用以壓制 GC
 */
@Slf4j
@Component
public class OrderProcessor {
    // --- 持久化數據指標 ---
    private final ChronicleMap<Long, Order> allOrdersDiskMap = Storage.self().orders();
    private final ChronicleMap<Long, Boolean> activeOrderIdDiskMap = Storage.self().activeOrders();
    private final ChronicleMap<Long, Trade> tradeHistoryDiskMap = Storage.self().trades();
    private final ChronicleMap<CidKey, Long> clientOrderIdDiskMap = Storage.self().cids();

    private final Ledger ledger;
    private final ExecutionReporter reporter;
    
    // --- 內存加速索引 ---
    private final Int2ObjectHashMap<OrderBook> symbolOrderBookMap = new Int2ObjectHashMap<>();
    private final Long2ObjectHashMap<Order> activeOrderMemoryIndex = new Long2ObjectHashMap<>();
    
    // --- 物件復用池 (Zero-GC 關鍵) ---
    private final OrderCreateDecoder decoder = new OrderCreateDecoder();
    private final Order reusableOrder = new Order();
    private final Trade reusableTrade = new Trade();
    private final CidKey reusableCidKey = new CidKey();

    // 暫存成交上下文
    private long currentGwSeq;
    private long currentTimestamp;

    public OrderProcessor(Ledger ledger, ExecutionReporter reporter) {
        this.ledger = ledger;
        this.reporter = reporter;
    }

    public void rebuildState() {
        log.info("OrderProcessor 正在恢復內存索引 (Zero-GC Mode)...");
        // 使用 ChronicleMap 的迭代器避免產生 KeySet 物件
        activeOrderIdDiskMap.forEach((id, active) -> {
            // 使用 getUsing 零分配讀取
            Order o = allOrdersDiskMap.getUsing(id, new Order()); // 恢復時使用新物件是安全的
            if (o != null && o.getStatus() < 2) {
                symbolOrderBookMap.computeIfAbsent(o.getSymbolId(), OrderBook::new).add(o);
                activeOrderMemoryIndex.put(id, o);
            } else activeOrderIdDiskMap.remove(id);
        });
    }

    public void processCreateCommand(UnsafeBuffer payload, long gwSeq, Supplier<Long> orderIdSupplier, Supplier<Long> tradeIdSupplier) {
        SbeCodec.decode(payload, 0, decoder);
        
        // 復用 CidKey 進行冪等檢查
        reusableCidKey.setUserId(decoder.userId());
        reusableCidKey.setClientOrderId(decoder.clientOrderId());
        
        Long existingOid = clientOrderIdDiskMap.get(reusableCidKey);
        if (existingOid != null) {
            Order o = allOrdersDiskMap.getUsing(existingOid, reusableOrder);
            if (o != null) reporter.resendReport(o, gwSeq);
            return;
        }
        
        handleOrderCreate(decoder, gwSeq, orderIdSupplier.get(), tradeIdSupplier);
    }

    private void handleOrderCreate(OrderCreateDecoder sbe, long gwSeq, long orderId, Supplier<Long> tradeIdSupplier) {
        this.currentGwSeq = gwSeq;
        this.currentTimestamp = sbe.timestamp();
        boolean isBuy = sbe.side() == Side.BUY;
        long cost = isBuy ? DecimalUtil.mulCeil(sbe.price(), sbe.qty()) : sbe.qty();
        int aid = isBuy ? Asset.USDT : Asset.BTC;

        if (!ledger.tryFreeze(sbe.userId(), aid, gwSeq, cost)) {
            reporter.sendReport(sbe.userId(), 0, sbe.clientOrderId(), OrderStatus.REJECTED, 0, 0, 0, 0, currentTimestamp, gwSeq);
            return;
        }

        // 建立新訂單實體 (此處分配是必要的，因為要掛入內存訂單簿)
        Order taker = new Order();
        taker.setOrderId(orderId); taker.setClientOrderId(sbe.clientOrderId()); taker.setUserId(sbe.userId());
        taker.setSymbolId((int)sbe.symbolId()); taker.setPrice(sbe.price()); taker.setQty(sbe.qty());
        taker.setSide((byte)(isBuy ? 0 : 1)); taker.setStatus((byte)0);
        taker.setVersion(1); taker.setLastSeq(gwSeq);

        activeOrderMemoryIndex.put(orderId, taker); 
        persistOrder(taker);

        // 執行撮合 (回調模式)
        symbolOrderBookMap.computeIfAbsent(taker.getSymbolId(), OrderBook::new)
                .match(taker, (mUid, tUid, price, qty, mOid) -> {
                    long tid = tradeIdSupplier.get();
                    
                    // 復用 Trade 物件進行持久化
                    reusableTrade.setTradeId(tid); reusableTrade.setOrderId(mOid); 
                    reusableTrade.setPrice(price); reusableTrade.setQty(qty); 
                    reusableTrade.setTime(currentTimestamp); reusableTrade.setLastSeq(currentGwSeq);
                    tradeHistoryDiskMap.put(tid, reusableTrade);

                    // 結算與狀態更新
                    processTradeLedger(mUid, tUid, price, qty, taker);
                    Order maker = activeOrderMemoryIndex.get(mOid);
                    if (maker != null) syncOrder(maker, currentGwSeq);
                    
                    reporter.sendReport(mUid, mOid, "", OrderStatus.PARTIALLY_FILLED, price, qty, 0, 0, currentTimestamp, currentGwSeq);
                });

        syncOrder(taker, gwSeq);
        reporter.sendReport(taker.getUserId(), orderId, taker.getClientOrderId(), 
                taker.getStatus() == 2 ? OrderStatus.FILLED : OrderStatus.NEW, 
                0, 0, taker.getFilled(), 0, currentTimestamp, gwSeq);
        
        clientOrderIdDiskMap.put(new CidKey(taker.getUserId(), taker.getClientOrderId()), orderId);
    }

    private void processTradeLedger(long makerUid, long takerUid, long price, long qty, Order taker) {
        long floor = DecimalUtil.mulFloor(price, qty);
        long ceil = DecimalUtil.mulCeil(price, qty);
        if (taker.getSide() == 0) {
            ledger.tradeSettleWithRefund(takerUid, Asset.USDT, ceil, DecimalUtil.mulCeil(taker.getPrice(), qty), Asset.BTC, qty, currentGwSeq);
            ledger.tradeSettle(makerUid, Asset.BTC, qty, Asset.USDT, floor, currentGwSeq);
        } else {
            ledger.tradeSettle(takerUid, Asset.BTC, qty, Asset.USDT, floor, currentGwSeq);
            Order m = activeOrderMemoryIndex.get(taker.getOrderId()); // 注意：邏輯與之前保持一致
            ledger.tradeSettleWithRefund(makerUid, Asset.USDT, ceil, m != null ? DecimalUtil.mulCeil(m.getPrice(), qty) : ceil, Asset.BTC, qty, currentGwSeq);
        }
        if (ceil > floor) ledger.addAvailable(PLATFORM_USER_ID, Asset.USDT, currentGwSeq, ceil - floor);
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
