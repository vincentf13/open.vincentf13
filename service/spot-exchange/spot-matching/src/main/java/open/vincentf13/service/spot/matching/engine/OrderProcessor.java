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
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 訂單處理核心 (Order Processor)
 職責：領域編排者，協調自持物件池的 OrderBook 與帳務系統
 */
@Slf4j
@Component
public class OrderProcessor {
    private final ChronicleMap<Long, Order> allOrdersDiskMap = Storage.self().orders();
    private final ChronicleMap<CidKey, Long> clientOrderIdDiskMap = Storage.self().cids();

    private final Ledger ledger;
    private final ExecutionReporter reporter;
    private final Int2ObjectHashMap<OrderBook> symbolOrderBookMap = new Int2ObjectHashMap<>();
    
    private final OrderCreateDecoder decoder = new OrderCreateDecoder();
    private final Order reusableOrder = new Order();
    private final CidKey reusableCidKey = new CidKey();
    private final byte[] tempCidBytes = new byte[32];

    public OrderProcessor(Ledger ledger, ExecutionReporter reporter) {
        this.ledger = ledger;
        this.reporter = reporter;
    }

    public void rebuildState() {
        log.info("OrderProcessor 正在恢復領域模型狀態...");
        OrderBook.rebuildAll(this::getOrAddBook);
    }

    public void processCreateCommand(UnsafeBuffer payload, long gwSeq, Supplier<Long> orderIdSupplier, Supplier<Long> tradeIdSupplier) {
        SbeCodec.decode(payload, 0, decoder);
        decoder.getClientOrderId(tempCidBytes, 0);
        reusableCidKey.set(decoder.userId(), tempCidBytes, 0, 32);
        
        if (clientOrderIdDiskMap.containsKey(reusableCidKey)) return;
        
        handleOrderCreate(decoder, gwSeq, orderIdSupplier.get(), tradeIdSupplier);
    }

    private void handleOrderCreate(OrderCreateDecoder sbe, long gwSeq, long orderId, Supplier<Long> tradeIdSupplier) {
        boolean isBuy = sbe.side() == Side.BUY;
        long cost = isBuy ? DecimalUtil.mulCeil(sbe.price(), sbe.qty()) : sbe.qty();

        if (!ledger.tryFreeze(sbe.userId(), isBuy ? Asset.USDT : Asset.BTC, gwSeq, cost)) {
            reporter.sendReport(sbe.userId(), 0, sbe.clientOrderId(), OrderStatus.REJECTED, 0, 0, 0, 0, sbe.timestamp());
            reporter.flushBatch(gwSeq);
            return;
        }

        // --- 零分配關鍵：從對應交易對的物件池借用 Order 實體 ---
        OrderBook book = getOrAddBook((int)sbe.symbolId());
        Order taker = book.borrowOrder();
        
        // 數據填充
        taker.setOrderId(orderId); taker.setClientOrderId(sbe.clientOrderId()); taker.setUserId(sbe.userId());
        taker.setSymbolId((int)sbe.symbolId()); taker.setPrice(sbe.price()); taker.setQty(sbe.qty());
        taker.setSide((byte)(isBuy ? 0 : 1)); taker.setStatus((byte)0);
        taker.setVersion(1); taker.setLastSeq(gwSeq);

        // 執行撮合 (內部自動處理歸還)
        book.match(taker, gwSeq, sbe.timestamp(), tradeIdSupplier, (maker, p, q) -> {
            processTradeLedger(maker.getUserId(), taker.getUserId(), p, q, taker, gwSeq);
            reporter.sendReport(maker.getUserId(), maker.getOrderId(), "", OrderStatus.PARTIALLY_FILLED, p, q, 0, 0, sbe.timestamp());
        });

        // Taker 結案同步
        book.syncOrder(taker, gwSeq);
        
        reporter.sendReport(taker.getUserId(), orderId, taker.getClientOrderId(), 
                taker.getStatus() == 2 ? OrderStatus.FILLED : OrderStatus.NEW, 
                0, 0, taker.getFilled(), 0, sbe.timestamp());
        
        reporter.flushBatch(gwSeq);
        clientOrderIdDiskMap.put(new CidKey(taker.getUserId(), tempCidBytes), orderId);
    }

    private void processTradeLedger(long makerUid, long takerUid, long price, long qty, Order taker, long seq) {
        long floor = DecimalUtil.mulFloor(price, qty), ceil = DecimalUtil.mulCeil(price, qty);
        if (taker.getSide() == 0) {
            ledger.tradeSettleWithRefund(takerUid, Asset.USDT, ceil, DecimalUtil.mulCeil(taker.getPrice(), qty), Asset.BTC, qty, seq);
            ledger.tradeSettle(makerUid, Asset.BTC, qty, Asset.USDT, floor, seq);
        } else {
            ledger.tradeSettle(takerUid, Asset.BTC, qty, Asset.USDT, floor, seq);
            ledger.tradeSettle(makerUid, Asset.USDT, ceil, Asset.BTC, qty, seq);
        }
        if (ceil > floor) ledger.addAvailable(PLATFORM_USER_ID, Asset.USDT, seq, ceil - floor);
    }

    private OrderBook getOrAddBook(int symbolId) {
        return symbolOrderBookMap.computeIfAbsent(symbolId, id -> new OrderBook(id));
    }
}
