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
public class OrderProcessor implements OrderBook.TradeFinalizer {
    private final ChronicleMap<CidKey, Long> clientOrderIdDiskMap = Storage.self().cids();

    private final Ledger ledger;
    private final ExecutionReporter reporter;
    
    private final OrderCreateDecoder decoder = new OrderCreateDecoder();
    private final CidKey reusableCidKey = new CidKey();

    // 執行上下文
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
        decoder.getClientOrderId(); // 此處 decoder 內置 long 讀取
        reusableCidKey.set(decoder.userId(), decoder.clientOrderId());
        
        if (clientOrderIdDiskMap.containsKey(reusableCidKey)) return;
        
        handleOrderCreate(decoder, gwSeq, orderIdSupplier.get(), tradeIdSupplier);
    }

    private void handleOrderCreate(OrderCreateDecoder sbe, long gwSeq, long orderId, Supplier<Long> tradeIdSupplier) {
        final long cid = sbe.clientOrderId();
        boolean isBuy = sbe.side() == Side.BUY;
        long cost = isBuy ? DecimalUtil.mulCeil(sbe.price(), sbe.qty()) : sbe.qty();

        // 1. 風控校驗
        if (!ledger.freezeBalance(sbe.userId(), isBuy ? Asset.USDT : Asset.BTC, cost, gwSeq)) {
            reporter.sendReport(sbe.userId(), 0, cid, OrderStatus.REJECTED, 0, 0, 0, 0, sbe.timestamp());
            reporter.flushBatch(gwSeq);
            // 修正點：記錄拒絕訂單的冪等映射
            reusableCidKey.set(sbe.userId(), cid);
            clientOrderIdDiskMap.put(reusableCidKey, ID_REJECTED);
            return;
        }

        // 2. 設置執行上下文供 onMatch 使用
        this.ctxGwSeq = gwSeq;
        this.ctxTimestamp = sbe.timestamp();
        this.ctxUserId = sbe.userId();
        this.ctxPrice = sbe.price();
        this.ctxSide = (byte)(isBuy ? OrderSide.BUY : OrderSide.SELL);

        // 3. 領域執行
        OrderBook book = OrderBook.get(sbe.symbolId());
        Order taker = book.handleCreate(orderId, sbe, cid, gwSeq, tradeIdSupplier, this);

    // 4. Taker 最終回報
        reporter.sendReport(taker.getUserId(), orderId, cid, 
                toSbeStatus(taker.getStatus()), 
                0, 0, taker.getFilled(), 0, ctxTimestamp);
        
        // 5. 批量落地與最終冪等存檔
        reporter.flushBatch(gwSeq);
        reusableCidKey.set(taker.getUserId(), cid);
        clientOrderIdDiskMap.put(reusableCidKey, orderId);

        if (taker.getStatus() == 2) book.releaseOrder(taker);
    }

    private OrderStatus toSbeStatus(byte status) {
        return switch (status) {
            case 1 -> OrderStatus.PARTIALLY_FILLED;
            case 2 -> OrderStatus.FILLED;
            default -> OrderStatus.NEW;
        };
    }

    @Override
    public void onMatch(long tradeId, Order maker, long price, long qty) {
        ledger.settleTrade(maker.getUserId(), ctxUserId, price, qty, ctxSide, ctxPrice, ctxGwSeq);
        
        // 1. Maker 回報
        reporter.sendReport(maker.getUserId(), maker.getOrderId(), maker.getClientOrderId(), 
                maker.getFilled() == maker.getQty() ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED, 
                price, qty, maker.getFilled(), maker.getPrice(), ctxTimestamp);
        
        // 2. Taker 回報 (針對此筆成交)
        reporter.sendReport(ctxUserId, 0, reusableCidKey.getClientOrderId(),
                OrderStatus.PARTIALLY_FILLED, // 這裡僅代表成交發生，最終狀態由 handleOrderCreate 發送
                price, qty, 0, 0, ctxTimestamp);
    }
}
