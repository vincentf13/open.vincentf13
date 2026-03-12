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
    /** 僅保留全系統唯一的冪等性索引 */
    private final ChronicleMap<CidKey, Long> clientOrderIdDiskMap = Storage.self().cids();

    private final Ledger ledger;
    private final ExecutionReporter reporter;
    
    // --- 預分配組件 ---
    private final OrderCreateDecoder decoder = new OrderCreateDecoder();
    private final CidKey reusableCidKey = new CidKey();
    private final byte[] tempCidBytes = new byte[32];

    public OrderProcessor(Ledger ledger, ExecutionReporter reporter) {
        this.ledger = ledger;
        this.reporter = reporter;
    }

    /** 啟動恢復：委派領域內部的全局重建 */
    public void rebuildState() {
        log.info("OrderProcessor 正在恢復領域模型狀態...");
        OrderBook.rebuildAll();
    }

    public void processCreateCommand(UnsafeBuffer payload, long gwSeq, Supplier<Long> orderIdSupplier, Supplier<Long> tradeIdSupplier) {
        SbeCodec.decode(payload, 0, decoder);
        decoder.getClientOrderId(tempCidBytes, 0);
        reusableCidKey.set(decoder.userId(), tempCidBytes, 0, 32);
        
        // 冪等過濾
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

        String cidStr = sbe.clientOrderId();

        // 2. 領域執行：Book 自主負責 Admission -> Match -> Sync 閉環
        OrderBook book = OrderBook.get(sbe.symbolId());
        Order taker = book.handleCreate(orderId, sbe, cidStr, gwSeq, tradeIdSupplier, (maker, p, q) -> {
            // 3. 跨領域協調：帳務結算 (下沉至 Ledger)
            ledger.settleTrade(maker.getUserId(), sbe.userId(), p, q, (sbe.side() == Side.BUY ? (byte)0 : (byte)1), sbe.price(), gwSeq);
            
            // 4. 產生 Maker 成交回報
            reporter.sendReport(maker.getUserId(), maker.getOrderId(), "", 
                    maker.getFilled() == maker.getQty() ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED, 
                    p, q, maker.getFilled(), maker.getPrice(), sbe.timestamp());
        });

        // 5. 產生 Taker 最終回報
        reporter.sendReport(taker.getUserId(), orderId, cidStr, 
                taker.getStatus() == 2 ? OrderStatus.FILLED : OrderStatus.NEW, 
                0, 0, taker.getFilled(), 0, sbe.timestamp());
        
        // 6. 批量落地與最終冪等存檔
        reporter.flushBatch(gwSeq);
        clientOrderIdDiskMap.put(new CidKey(taker.getUserId(), tempCidBytes), orderId);

        // 7. 資源安全歸還
        if (taker.getStatus() == 2) book.releaseOrder(taker);
    }
}
