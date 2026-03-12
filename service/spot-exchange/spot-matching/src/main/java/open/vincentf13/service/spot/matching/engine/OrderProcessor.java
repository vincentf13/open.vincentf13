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
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

import static open.vincentf13.service.spot.infra.Constants.Asset;
import static open.vincentf13.service.spot.infra.Constants.PLATFORM_USER_ID;

/**
 訂單處理核心 (Order Processor)
 職責：執行訂單業務邏輯，實施指令級冪等過濾
 */
@Slf4j
@Component
public class OrderProcessor {
    private final ChronicleMap<Long, Order> allOrdersDiskMap = Storage.self().orders();
    private final ChronicleMap<Long, Boolean> activeOrderIdDiskMap = Storage.self().activeOrders();
    private final ChronicleMap<Long, Trade> tradeHistoryDiskMap = Storage.self().trades();
    private final ChronicleMap<CidKey, Long> clientOrderIdDiskMap = Storage.self().cids();
    
    private final Ledger ledger;
    private final ExecutionReporter reporter;
    
    private final Int2ObjectHashMap<OrderBook> symbolOrderBookMap = new Int2ObjectHashMap<>();
    private final Long2ObjectHashMap<Order> activeOrderMemoryIndex = new Long2ObjectHashMap<>(100_000, 0.5f);
    
    private final OrderCreateDecoder decoder = new OrderCreateDecoder();
    private final Trade reusableTrade = new Trade();
    private final CidKey reusableCidKey = new CidKey();
    private final byte[] tempCidBytes = new byte[32];
    
    private long currentGwSeq;
    private long currentTimestamp;
    
    public OrderProcessor(Ledger ledger,
                          ExecutionReporter reporter) {
        this.ledger = ledger;
        this.reporter = reporter;
    }
    
    public void rebuildState() {
        log.info("OrderProcessor 恢復內存狀態...");
        activeOrderIdDiskMap.forEach((id, active) -> {
            Order o = allOrdersDiskMap.getUsing(id, new Order());
            if (o != null && o.getStatus() < 2) {
                symbolOrderBookMap.computeIfAbsent(o.getSymbolId(), OrderBook::new).add(o);
                activeOrderMemoryIndex.put(id, o);
            }
        });
    }
    
    /**
     處理訂單指令入口
     優化：發現重複指令直接返回，僅保留靜默過濾能力
     */
    public void processCreateCommand(UnsafeBuffer payload,
                                     long gwSeq,
                                     Supplier<Long> orderIdSupplier,
                                     Supplier<Long> tradeIdSupplier) {
        SbeCodec.decode(payload, 0, decoder);
        decoder.getClientOrderId(tempCidBytes, 0);
        reusableCidKey.set(decoder.userId(), tempCidBytes, 0, 32);
        
        // 冪等性過濾：如果已處理過，則直接跳過 (不再重發回報)
        if (clientOrderIdDiskMap.containsKey(reusableCidKey)) {
            return;
        }
        
        handleOrderCreate(decoder, gwSeq, orderIdSupplier.get(), tradeIdSupplier);
    }
    
    private void handleOrderCreate(OrderCreateDecoder sbe,
                                   long gwSeq,
                                   long orderId,
                                   Supplier<Long> tradeIdSupplier) {
        this.currentGwSeq = gwSeq;
        this.currentTimestamp = sbe.timestamp();
        boolean isBuy = sbe.side() == Side.BUY;
        long cost = isBuy ? DecimalUtil.mulCeil(sbe.price(), sbe.qty()) : sbe.qty();
        
        if (!ledger.tryFreeze(sbe.userId(), isBuy ? Asset.USDT : Asset.BTC, gwSeq, cost)) {
            reporter.sendReport(sbe.userId(), 0, sbe.clientOrderId(), OrderStatus.REJECTED, 0, 0, 0, 0, sbe.timestamp());
            reporter.flushBatch(gwSeq);
            return;
        }
        
        Order taker = new Order();
        taker.setOrderId(orderId);
        taker.setClientOrderId(sbe.clientOrderId());
        taker.setUserId(sbe.userId());
        taker.setSymbolId((int) sbe.symbolId());
        taker.setPrice(sbe.price());
        taker.setQty(sbe.qty());
        taker.setSide((byte) (isBuy ? 0 : 1));
        taker.setStatus((byte) 0);
        taker.setVersion(1);
        taker.setLastSeq(gwSeq);
        
        activeOrderMemoryIndex.put(orderId, taker);
        
        symbolOrderBookMap.computeIfAbsent(taker.getSymbolId(), OrderBook::new)
                          .match(taker, (mUid, tUid, price, qty, mOid) -> {
                              long tid = tradeIdSupplier.get();
                              reusableTrade.setTradeId(tid);
                              reusableTrade.setOrderId(mOid);
                              reusableTrade.setPrice(price);
                              reusableTrade.setQty(qty);
                              reusableTrade.setTime(sbe.timestamp());
                              reusableTrade.setLastSeq(gwSeq);
                              tradeHistoryDiskMap.put(tid, reusableTrade);
                              
                              processTradeLedger(mUid, tUid, price, qty, taker, gwSeq);
                              Order maker = activeOrderMemoryIndex.get(mOid);
                              if (maker != null)
                                  syncOrder(maker, gwSeq);
                              
                              reporter.sendReport(mUid, mOid, "", OrderStatus.PARTIALLY_FILLED, price, qty, 0, 0, sbe.timestamp());
                          });
        
        syncOrder(taker, gwSeq);
        reporter.sendReport(taker.getUserId(), orderId, taker.getClientOrderId(),
                            taker.getStatus() == 2 ? OrderStatus.FILLED : OrderStatus.NEW,
                            0, 0, taker.getFilled(), 0, sbe.timestamp());
        
        reporter.flushBatch(gwSeq);
        
        clientOrderIdDiskMap.put(new CidKey(taker.getUserId(), tempCidBytes), orderId);
    }
    
    private void processTradeLedger(long makerUid,
                                    long takerUid,
                                    long price,
                                    long qty,
                                    Order taker,
                                    long seq) {
        long floor = DecimalUtil.mulFloor(price, qty), ceil = DecimalUtil.mulCeil(price, qty);
        if (taker.getSide() == 0) {
            ledger.tradeSettleWithRefund(takerUid, Asset.USDT, ceil, DecimalUtil.mulCeil(taker.getPrice(), qty), Asset.BTC, qty, seq);
            ledger.tradeSettle(makerUid, Asset.BTC, qty, Asset.USDT, floor, seq);
        } else {
            ledger.tradeSettle(takerUid, Asset.BTC, qty, Asset.USDT, floor, seq);
            ledger.tradeSettle(makerUid, Asset.USDT, ceil, Asset.BTC, qty, seq);
        }
        if (ceil > floor)
            ledger.addAvailable(PLATFORM_USER_ID, Asset.USDT, seq, ceil - floor);
    }
    
    public void syncOrder(Order o,
                          long gwSeq) {
        if (o.getFilled() == o.getQty()) {
            o.setStatus((byte) 2);
            activeOrderMemoryIndex.remove(o.getOrderId());
        } else if (o.getFilled() > 0)
            o.setStatus((byte) 1);
        o.setVersion(o.getVersion() + 1);
        o.setLastSeq(gwSeq);
        allOrdersDiskMap.put(o.getOrderId(), o);
        if (o.getStatus() < 2)
            activeOrderIdDiskMap.put(o.getOrderId(), true);
        else
            activeOrderIdDiskMap.remove(o.getOrderId());
    }
}
