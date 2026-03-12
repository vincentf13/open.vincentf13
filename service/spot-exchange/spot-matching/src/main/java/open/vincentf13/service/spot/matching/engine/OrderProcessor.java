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
 訂單處理核心 (Order Processor) - 終極 Zero-Allocation 版
 職責：領域編排者，協調風控、訂單簿狀態機與外部回報發送
 */
@Slf4j
@Component
public class OrderProcessor implements OrderBook.TradeFinalizer {
    private final ChronicleMap<CidKey, Long> clientOrderIdDiskMap = Storage.self().cids();

    private final Ledger ledger;
    private final ExecutionReporter reporter;
    
    // --- 預分配組件 ---
    private final OrderCreateDecoder decoder = new OrderCreateDecoder();
    private final CidKey reusableCidKey = new CidKey();

    // --- 執行上下文 (用於消除 Lambda 捕獲) ---
    private long ctxGwSeq;
    private long ctxTimestamp;
    private long ctxUserId;
    private long ctxPrice;
    private byte ctxSide;

    public OrderProcessor(Ledger ledger, ExecutionReporter reporter) {
        this.ledger = ledger;
        this.reporter = reporter;
    }

    public void rebuildState() {
        log.info("OrderProcessor 正在恢復領域模型狀態...");
        OrderBook.rebuildAll();
    }

    public void processCreateCommand(UnsafeBuffer payload, long gwSeq, Supplier<Long> orderIdSupplier, Supplier<Long> tradeIdSupplier) {
        SbeCodec.decode(payload, 0, decoder);
        
        final long cid = decoder.clientOrderId();
        reusableCidKey.set(decoder.userId(), cid);
        
        // 冪等過濾：零分配檢查
        if (clientOrderIdDiskMap.containsKey(reusableCidKey)) return;
        
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

        // 2. 設置執行上下文
        this.ctxGwSeq = gwSeq;
        this.ctxTimestamp = sbe.timestamp();
        this.ctxUserId = sbe.userId();
        this.ctxPrice = sbe.price();
        this.ctxSide = (byte)(isBuy ? OrderSide.BUY : OrderSide.SELL);

        // 3. 領域執行
        OrderBook book = OrderBook.get(sbe.symbolId());
        Order taker = book.handleCreate(orderId, sbe, cid, gwSeq, tradeIdSupplier, this);

        // 4. 最終回報
        reporter.sendReport(taker.getUserId(), orderId, cid, 
                taker.getStatus() == 2 ? OrderStatus.FILLED : OrderStatus.NEW, 
                0, 0, taker.getFilled(), 0, ctxTimestamp);
        
        // 5. 批量落地與最終冪等存檔 (使用復用對象，徹底消除 new CidKey)
        reporter.flushBatch(gwSeq);
        reusableCidKey.set(taker.getUserId(), cid);
        clientOrderIdDiskMap.put(reusableCidKey, orderId);

        // 6. 資源安全歸還
        if (taker.getStatus() == 2) book.releaseOrder(taker);
    }

    @Override
    public void onMatch(Order maker, long price, long qty) {
        ledger.settleTrade(maker.getUserId(), ctxUserId, price, qty, ctxSide, ctxPrice, ctxGwSeq);
        
        reporter.sendReport(maker.getUserId(), maker.getOrderId(), maker.getClientOrderId(), 
                maker.getFilled() == maker.getQty() ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED, 
                price, qty, maker.getFilled(), maker.getPrice(), ctxTimestamp);
    }
}
