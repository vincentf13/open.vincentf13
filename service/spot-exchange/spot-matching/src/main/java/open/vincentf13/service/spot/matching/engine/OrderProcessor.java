package open.vincentf13.service.spot.matching.engine;

import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.sbe.SbeCodec;
import open.vincentf13.service.spot.infra.util.DecimalUtil;
import open.vincentf13.service.spot.model.*;
import open.vincentf13.service.spot.sbe.*;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 訂單處理核心 (Order Processor)
 職責：領域編排者，協調風控、訂單簿狀態機與外部回報發送
 */
@Slf4j
@Component
public class OrderProcessor {
    private final ChronicleMap<CidKey, Long> clientOrderIdDiskMap = Storage.self().cids();
    private final ChronicleMap<Long, Order> allOrdersDiskMap = Storage.self().orders();

    private final Ledger ledger;
    private final ExecutionReporter reporter;
    
    private final OrderCreateDecoder decoder = new OrderCreateDecoder();
    private final Order reusableOrder = new Order();
    private final CidKey reusableCidKey = new CidKey();

    public OrderProcessor(Ledger ledger, ExecutionReporter reporter) {
        this.ledger = ledger;
        this.reporter = reporter;
    }

    /** 啟動恢復：委派領域內部的全局重建 (Zero-GC) */
    public void rebuildState() {
        log.info("OrderProcessor 正在恢復領域模型狀態...");
        OrderBook.rebuildAll();
    }

    public void processCreateCommand(UnsafeBuffer payload, long gwSeq, Supplier<Long> orderIdSupplier, Supplier<Long> tradeIdSupplier) {
        SbeCodec.decode(payload, 0, decoder);
        
        final long cid = decoder.clientOrderId();
        reusableCidKey.set(decoder.userId(), cid);
        
        // 冪等過濾：零分配檢查
        if (clientOrderIdDiskMap.containsKey(reusableCidKey)) {
            // 實時模式下嘗試補發最後一次回報 (可選)
            return;
        }
        
        handleOrderCreate(decoder, gwSeq, orderIdSupplier.get(), tradeIdSupplier);
    }

    private void handleOrderCreate(OrderCreateDecoder sbe, long gwSeq, long orderId, Supplier<Long> tradeIdSupplier) {
        final long cid = sbe.clientOrderId();
        final boolean isBuy = sbe.side() == Side.BUY;
        final long cost = isBuy ? DecimalUtil.mulCeil(sbe.price(), sbe.qty()) : sbe.qty();

        // 1. 風控校驗
        if (!ledger.freezeBalance(sbe.userId(), isBuy ? Asset.USDT : Asset.BTC, cost, gwSeq)) {
            reporter.sendReport(sbe.userId(), 0, cid, OrderStatus.REJECTED, 0, 0, 0, 0, sbe.timestamp());
            reporter.flushBatch(gwSeq);
            return;
        }

        // 2. 領域處理：從池中借用、撮合、同步、資源回收判定
        OrderBook book = OrderBook.get(sbe.symbolId());
        Order taker = book.handleCreate(orderId, sbe, cid, gwSeq, tradeIdSupplier, (maker, p, q) -> {
            ledger.settleTrade(maker.getUserId(), sbe.userId(), p, q, (sbe.side() == Side.BUY ? (byte)0 : (byte)1), sbe.price(), gwSeq);
            reporter.sendReport(maker.getUserId(), maker.getOrderId(), maker.getClientOrderId(), 
                    maker.getFilled() == maker.getQty() ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED, 
                    p, q, maker.getFilled(), maker.getPrice(), sbe.timestamp());
        });

        // 3. 最終回報
        reporter.sendReport(taker.getUserId(), orderId, cid, 
                taker.getStatus() == 2 ? OrderStatus.FILLED : OrderStatus.NEW, 
                0, 0, taker.getFilled(), 0, sbe.timestamp());
        
        // 4. 批量寫入與最終冪等存檔 (使用復用 Key)
        reporter.flushBatch(gwSeq);
        reusableCidKey.set(taker.getUserId(), cid);
        clientOrderIdDiskMap.put(reusableCidKey, orderId);

        // 5. 資源安全釋放：僅在完全成交且未入簿時回收
        if (taker.getStatus() == 2) book.releaseOrder(taker);
    }
}
