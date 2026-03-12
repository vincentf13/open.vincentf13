package open.vincentf13.service.spot.matching.engine;

import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.sbe.SbeCodec;
import open.vincentf13.service.spot.infra.util.DecimalUtil;
import open.vincentf13.service.spot.model.*;
import open.vincentf13.service.spot.sbe.*;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 訂單處理核心 (Order Processor)
 職責：執行訂單生命週期管理，實施「磁碟持久化 + 內存加速」雙層存儲架構
 */
@Slf4j
@Component
public class OrderProcessor {
    // --- 持久化數據層 (Source of Truth - 磁碟 WAL) ---
    private final ChronicleMap<Long, Order> allOrdersDiskMap = Storage.self().orders();
    private final ChronicleMap<Long, Boolean> activeOrderIdDiskMap = Storage.self().activeOrders();
    private final ChronicleMap<Long, Trade> tradeHistoryDiskMap = Storage.self().trades();
    private final ChronicleMap<CidKey, Long> clientOrderIdDiskMap = Storage.self().cids();

    // --- 內存加速層 (Computation View - 高速 Map) ---
    /** 交易對訂單簿：按 SymbolId 組織的內存價格隊列 */
    private final Int2ObjectHashMap<OrderBook> symbolOrderBookMap = new Int2ObjectHashMap<>();
    
    /** 活躍對象索引：持有內存訂單實體的直接指標，實現微秒級狀態更新，避開磁碟反序列化開銷 */
    private final Long2ObjectHashMap<Order> activeOrderMemoryIndex = new Long2ObjectHashMap<>(100_000, 0.5f);
    
    // --- 零分配復用池 (Zero-GC Optimization) ---
    private final OrderCreateDecoder decoder = new OrderCreateDecoder();
    private final Order reusableOrder = new Order();
    private final Trade reusableTrade = new Trade();
    private final CidKey reusableCidKey = new CidKey();
    /** 指令級緩衝：直接提取 SBE 位元組流，消除熱點路徑中的 String 分配與 GC 抖動 */
    private final byte[] tempCidBytes = new byte[32];

    private final Ledger ledger;
    private final ExecutionReporter reporter;
    private long currentGwSeq;
    private long currentTimestamp;

    public OrderProcessor(Ledger ledger, ExecutionReporter reporter) {
        this.ledger = ledger;
        this.reporter = reporter;
    }

    public void rebuildState() {
        log.info("OrderProcessor 正在恢復內存視圖 (Zero-GC Mode)...");
        activeOrderIdDiskMap.forEach((id, active) -> {
            Order o = allOrdersDiskMap.getUsing(id, new Order()); 
            if (o != null && o.getStatus() < 2) {
                symbolOrderBookMap.computeIfAbsent(o.getSymbolId(), OrderBook::new).add(o);
                activeOrderMemoryIndex.put(id, o);
            }
        });
    }

    public void processCreateCommand(UnsafeBuffer payload, long gwSeq, Supplier<Long> orderIdSupplier, Supplier<Long> tradeIdSupplier) {
        SbeCodec.decode(payload, 0, decoder);
        
        // 零字串提取：直接比對二進位位元組
        decoder.getClientOrderId(tempCidBytes, 0);
        reusableCidKey.set(decoder.userId(), tempCidBytes, 0, 32);
        
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

        String cidStr = sbe.clientOrderId(); // 僅在發送回報時建立字串

        if (!ledger.tryFreeze(sbe.userId(), aid, gwSeq, cost)) {
            reporter.sendReport(sbe.userId(), 0, cidStr, OrderStatus.REJECTED, 0, 0, 0, 0, currentTimestamp, gwSeq);
            return;
        }

        Order taker = new Order();
        taker.setOrderId(orderId); taker.setClientOrderId(cidStr); taker.setUserId(sbe.userId());
        taker.setSymbolId((int)sbe.symbolId()); taker.setPrice(sbe.price()); taker.setQty(sbe.qty());
        taker.setSide((byte)(isBuy ? 0 : 1)); taker.setStatus((byte)0);
        taker.setVersion(1); taker.setLastSeq(gwSeq);

        activeOrderMemoryIndex.put(orderId, taker); 
        
        symbolOrderBookMap.computeIfAbsent(taker.getSymbolId(), OrderBook::new)
                .match(taker, (mUid, tUid, price, qty, mOid) -> {
                    long tid = tradeIdSupplier.get();
                    
                    // 零分配持久化：填充復用對象並寫入
                    reusableTrade.setTradeId(tid); reusableTrade.setOrderId(mOid); 
                    reusableTrade.setPrice(price); reusableTrade.setQty(qty); 
                    reusableTrade.setTime(currentTimestamp); reusableTrade.setLastSeq(currentGwSeq);
                    tradeHistoryDiskMap.put(tid, reusableTrade);

                    processTradeLedger(mUid, tUid, price, qty, taker);
                    Order maker = activeOrderMemoryIndex.get(mOid);
                    if (maker != null) syncOrder(maker, currentGwSeq);
                    
                    reporter.sendReport(mUid, mOid, "", OrderStatus.PARTIALLY_FILLED, price, qty, 0, 0, currentTimestamp, currentGwSeq);
                });

        syncOrder(taker, gwSeq);
        reporter.sendReport(taker.getUserId(), orderId, cidStr, 
                taker.getStatus() == 2 ? OrderStatus.FILLED : OrderStatus.NEW, 
                0, 0, taker.getFilled(), 0, currentTimestamp, gwSeq);
        
        clientOrderIdDiskMap.put(new CidKey(taker.getUserId(), tempCidBytes), orderId);
    }

    private void processTradeLedger(long makerUid, long takerUid, long price, long qty, Order taker) {
        long floor = DecimalUtil.mulFloor(price, qty), ceil = DecimalUtil.mulCeil(price, qty);
        if (taker.getSide() == 0) {
            ledger.tradeSettleWithRefund(takerUid, Asset.USDT, ceil, DecimalUtil.mulCeil(taker.getPrice(), qty), Asset.BTC, qty, currentGwSeq);
            ledger.tradeSettle(makerUid, Asset.BTC, qty, Asset.USDT, floor, currentGwSeq);
        } else {
            ledger.tradeSettle(takerUid, Asset.BTC, qty, Asset.USDT, floor, currentGwSeq);
            Order m = activeOrderMemoryIndex.get(taker.getOrderId());
            ledger.tradeSettleWithRefund(makerUid, Asset.USDT, ceil, m != null ? DecimalUtil.mulCeil(m.getPrice(), qty) : ceil, Asset.BTC, qty, currentGwSeq);
        }
        if (ceil > floor) ledger.addAvailable(PLATFORM_USER_ID, Asset.USDT, currentGwSeq, ceil - floor);
    }

    /** 狀態同步：將內存變更同步至磁碟 Map，實現順序持久化 */
    public void syncOrder(Order o, long gwSeq) {
        if (o.getFilled() == o.getQty()) { 
            o.setStatus((byte)2); activeOrderMemoryIndex.remove(o.getOrderId()); 
        } else if (o.getFilled() > 0) o.setStatus((byte)1);
        o.setVersion(o.getVersion() + 1); o.setLastSeq(gwSeq); 
        allOrdersDiskMap.put(o.getOrderId(), o);
        if (o.getStatus() < 2) activeOrderIdDiskMap.put(o.getOrderId(), true);
        else activeOrderIdDiskMap.remove(o.getOrderId());
    }
}
