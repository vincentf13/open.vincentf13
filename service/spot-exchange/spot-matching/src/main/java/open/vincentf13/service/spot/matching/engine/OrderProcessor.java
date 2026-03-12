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
    private final Order reusableOrder = new Order();
    private final Trade reusableTrade = new Trade();
    private final CidKey reusableCidKey = new CidKey();
    private final byte[] tempCidBytes = new byte[32];

    private long currentGwSeq;
    private long currentTimestamp;

    public OrderProcessor(Ledger ledger, ExecutionReporter reporter) {
        this.ledger = ledger;
        this.reporter = reporter;
    }

    public void rebuildState() {
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

        if (!ledger.tryFreeze(sbe.userId(), isBuy ? Asset.USDT : Asset.BTC, gwSeq, cost)) {
            reporter.sendReport(sbe.userId(), 0, sbe.clientOrderId(), OrderStatus.REJECTED, 0, 0, 0, 0, currentTimestamp, gwSeq);
            return;
        }

        Order taker = new Order();
        taker.setOrderId(orderId); taker.setClientOrderId(sbe.clientOrderId()); taker.setUserId(sbe.userId());
        taker.setSymbolId((int)sbe.symbolId()); taker.setPrice(sbe.price()); taker.setQty(sbe.qty());
        taker.setSide((byte)(isBuy ? 0 : 1)); taker.setStatus((byte)0);
        taker.setVersion(1); taker.setLastSeq(gwSeq);

        // 性能優化：此處不立即執行 allOrdersDiskMap.put，待撮合完成後合併寫入
        activeOrderMemoryIndex.put(orderId, taker); 
        
        symbolOrderBookMap.computeIfAbsent(taker.getSymbolId(), OrderBook::new)
                .match(taker, (mUid, tUid, price, qty, mOid) -> {
                    long tid = tradeIdSupplier.get();
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
        reporter.sendReport(taker.getUserId(), orderId, taker.getClientOrderId(), 
                taker.getStatus() == 2 ? OrderStatus.FILLED : OrderStatus.NEW, 
                0, 0, taker.getFilled(), 0, currentTimestamp, gwSeq);
        
        clientOrderIdDiskMap.put(new CidKey(taker.getUserId(), tempCidBytes), orderId);
    }

    private void processTradeLedger(long makerUid, long takerUid, long price, long qty, Order taker) {
        long floor = DecimalUtil.mulFloor(price, qty), ceil = DecimalUtil.mulCeil(price, qty);
        if (taker.getSide() == 0) { // Taker BUY
            // Taker 依照實際成交價結算，並獲得溢價退款
            ledger.tradeSettleWithRefund(takerUid, Asset.USDT, ceil, DecimalUtil.mulCeil(taker.getPrice(), qty), Asset.BTC, qty, currentGwSeq);
            ledger.tradeSettle(makerUid, Asset.BTC, qty, Asset.USDT, floor, currentGwSeq);
        } else { // Taker SELL
            ledger.tradeSettle(takerUid, Asset.BTC, qty, Asset.USDT, floor, currentGwSeq);
            // Maker BUY (Maker 始終以自己的掛單價成交，無退款)
            ledger.tradeSettle(makerUid, Asset.USDT, ceil, Asset.BTC, qty, currentGwSeq);
        }
        if (ceil > floor) ledger.addAvailable(PLATFORM_USER_ID, Asset.USDT, currentGwSeq, ceil - floor);
    }

    public void syncOrder(Order o, long gwSeq) {
        if (o.getFilled() == o.getQty()) { 
            o.setStatus((byte)2); activeOrderMemoryIndex.remove(o.getOrderId()); 
        } else if (o.getFilled() > 0) o.setStatus((byte)1);
        o.setVersion(o.getVersion() + 1); o.setLastSeq(gwSeq); 
        
        // 核心持久化點：將最終狀態同步至磁碟
        allOrdersDiskMap.put(o.getOrderId(), o);
        if (o.getStatus() < 2) activeOrderIdDiskMap.put(o.getOrderId(), true);
        else activeOrderIdDiskMap.remove(o.getOrderId());
    }
}
