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
 職責：作為領域編排者，協調訂單簿狀態機與外部系統 (Ledger/Reporter)
 */
@Slf4j
@Component
public class OrderProcessor {
    private final ChronicleMap<CidKey, Long> clientOrderIdDiskMap = Storage.self().cids();
    private final ChronicleMap<Long, Order> allOrdersDiskMap = Storage.self().orders();

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

        // 1. 風控校驗
        if (!ledger.tryFreeze(sbe.userId(), isBuy ? Asset.USDT : Asset.BTC, gwSeq, cost)) {
            reporter.sendReport(sbe.userId(), 0, sbe.clientOrderId(), OrderStatus.REJECTED, 0, 0, 0, 0, sbe.timestamp());
            reporter.flushBatch(gwSeq);
            return;
        }

        // 2. 領域處理：Admission -> 撮合 -> 結案同步 (Book 自主處理)
        OrderBook book = getOrAddBook(sbe.symbolId());
        Order taker = book.processTaker(orderId, sbe, gwSeq, tradeIdSupplier, (maker, p, q) -> {
            // 3. 執行帳務結算
            ledger.settleTrade(maker.getUserId(), sbe.userId(), p, q, (sbe.side() == Side.BUY ? (byte)0 : (byte)1), sbe.price(), gwSeq);
            // 4. 處理 Maker 回報
            reporter.sendReport(maker.getUserId(), maker.getOrderId(), "", OrderStatus.PARTIALLY_FILLED, p, q, 0, 0, sbe.timestamp());
        });

        // 5. 處理 Taker 最終回報
        reporter.sendReport(taker.getUserId(), orderId, taker.getClientOrderId(), 
                taker.getStatus() == 2 ? OrderStatus.FILLED : OrderStatus.NEW, 
                0, 0, taker.getFilled(), 0, sbe.timestamp());
        
        reporter.flushBatch(gwSeq);
        clientOrderIdDiskMap.put(new CidKey(taker.getUserId(), tempCidBytes), orderId);

        // 安全釋放：結案後回收
        if (taker.getStatus() == 2) book.releaseOrder(taker);
    }

    private OrderBook getOrAddBook(int symbolId) {
        return symbolOrderBookMap.computeIfAbsent(symbolId, id -> new OrderBook(id));
    }
}
