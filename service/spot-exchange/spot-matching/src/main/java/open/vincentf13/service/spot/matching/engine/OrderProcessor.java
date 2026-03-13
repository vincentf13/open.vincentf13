package open.vincentf13.service.spot.matching.engine;

import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.sbe.SbeCodec;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
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

    public void coldStartRebuild() {
        log.warn("未檢測到有效內存快照，正在執行耗時的全量磁碟掃描以恢復狀態...");
        OrderBook.rebuildAll();
    }

    public void processCreateCommand(UnsafeBuffer payload, long gwSeq, Supplier<Long> orderIdSupplier, Supplier<Long> tradeIdSupplier) {
        OrderCreateDecoder decoder = ThreadContext.get().getOrderCreateDecoder();
        SbeCodec.decode(payload, decoder);
        decoder.clientOrderId(); // 此處 decoder 內置 long 讀取
        reusableCidKey.set(decoder.userId(), decoder.clientOrderId());

        if (clientOrderIdDiskMap.containsKey(reusableCidKey)) return;

        handleOrderCreate(decoder, gwSeq, orderIdSupplier.get(), tradeIdSupplier);
    }

    /** 
      處理撤單指令 
     */
    public void processCancelCommand(long userId, long orderId, long gwSeq) {
        Order o = Storage.self().orders().get(orderId);
        if (o == null || o.getUserId() != userId || o.getStatus() >= 2) {
            // 訂單不存在、不屬於該用戶或已結束，直接忽略
            return;
        }

        // 冪等檢查：如果訂單記錄的 lastSeq 已經是當前或更後，說明已處理過
        if (o.getLastSeq() >= gwSeq) return;

        // 1. 從領域模型移除 (OrderBook 會處理內存索引與物件池回收)
        OrderBook book = OrderBook.get(o.getSymbolId());
        book.remove(orderId);

        // 2. 資金解凍
        long unfreezeQty = o.getQty() - o.getFilled();
        if (unfreezeQty > 0) {
            int assetId = (o.getSide() == OrderSide.BUY) ? book.getQuoteAssetId() : book.getBaseAssetId();
            long amount = (o.getSide() == OrderSide.BUY) ? DecimalUtil.mulCeil(o.getPrice(), unfreezeQty) : unfreezeQty;
            ledger.unfreezeBalance(userId, assetId, amount, gwSeq);
        }

        // 3. 更新磁碟狀態為 CANCELED
        o.setStatus((byte) 3); // 3=CANCELED
        o.setLastSeq(gwSeq);
        book.syncOrder(o, gwSeq);

        // 4. 發送回報
        reporter.sendReport(userId, orderId, o.getClientOrderId(), OrderStatus.CANCELED, 
                0, 0, o.getFilled(), 0, System.currentTimeMillis(), gwSeq);

        log.info("訂單 {} 撤單成功 (userId: {}, gwSeq: {})", orderId, userId, gwSeq);
    }
    private void handleOrderCreate(OrderCreateDecoder sbe, long gwSeq, long orderId, Supplier<Long> tradeIdSupplier) {
        final long cid = sbe.clientOrderId();
        final long price = sbe.price();
        final long qty = sbe.qty();
        final long userId = sbe.userId();
        boolean isBuy = sbe.side() == Side.BUY;

        // 1. 參數合法性校驗 (Risk Check)
        if (price <= 0 || qty <= 0) {
            reporter.sendReport(userId, 0, cid, OrderStatus.REJECTED, 0, 0, 0, 0, sbe.timestamp(), gwSeq);
            reusableCidKey.set(userId, cid);
            clientOrderIdDiskMap.put(reusableCidKey, ID_REJECTED);
            log.warn("拒絕非法指令：UserId={}, Price={}, Qty={}", userId, price, qty);
            return;
        }

        // 3. 獲取領域物件
        OrderBook book = OrderBook.get(sbe.symbolId());
        int assetId = isBuy ? book.getQuoteAssetId() : book.getBaseAssetId();
        long cost = isBuy ? DecimalUtil.mulCeil(price, qty) : qty;

        // 2. 風控校驗 (餘額檢查)
        if (!ledger.freezeBalance(userId, assetId, cost, gwSeq)) {
            reporter.sendReport(userId, 0, cid, OrderStatus.REJECTED, 0, 0, 0, 0, sbe.timestamp(), gwSeq);
            reusableCidKey.set(userId, cid);
            clientOrderIdDiskMap.put(reusableCidKey, ID_REJECTED);
            return;
        }

        // 設置執行上下文
        this.ctxGwSeq = gwSeq;
        this.ctxTimestamp = sbe.timestamp();
        this.ctxUserId = userId;
        this.ctxPrice = price;
        this.ctxSide = (byte)(isBuy ? OrderSide.BUY : OrderSide.SELL);

        Order taker = book.handleCreate(orderId, sbe, cid, gwSeq, tradeIdSupplier, this);

        // 4. Taker 最終回報
        reporter.sendReport(taker.getUserId(), orderId, cid, 
                toSbeStatus(taker.getStatus()), 
                0, 0, taker.getFilled(), 0, ctxTimestamp, gwSeq);

        reusableCidKey.set(taker.getUserId(), cid);
        clientOrderIdDiskMap.put(reusableCidKey, orderId);

        if (taker.getStatus() == 2) book.releaseOrder(taker);
    }

    private OrderStatus toSbeStatus(byte status) {
        return switch (status) {
            case 1 -> OrderStatus.PARTIALLY_FILLED;
            case 2 -> OrderStatus.FILLED;
            case 3 -> OrderStatus.CANCELED;
            default -> OrderStatus.NEW;
        };
    }

    @Override
    public void onMatch(long tradeId, Order maker, long price, long qty, int baseAsset, int quoteAsset) {
        ledger.settleTrade(maker.getUserId(), ctxUserId, price, qty, ctxSide, ctxPrice, ctxGwSeq, baseAsset, quoteAsset, tradeId);

        // 1. Maker 回報
        reporter.sendReport(maker.getUserId(), maker.getOrderId(), maker.getClientOrderId(), 
                maker.getFilled() == maker.getQty() ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED, 
                price, qty, maker.getFilled(), price, ctxTimestamp, ctxGwSeq);

        // 2. Taker 回報 (針對此筆成交)
        reporter.sendReport(ctxUserId, 0, reusableCidKey.getClientOrderId(),
                OrderStatus.PARTIALLY_FILLED, 
                price, qty, 0, price, ctxTimestamp, ctxGwSeq);
    }

    @Override
    public void onSTP(Order maker, long gwSeq) {
        // 執行「取消舊單」策略
        long unfreezeQty = maker.getQty() - maker.getFilled();
        if (unfreezeQty > 0) {
            OrderBook book = OrderBook.get(maker.getSymbolId());
            int assetId = (maker.getSide() == OrderSide.BUY) ? book.getQuoteAssetId() : book.getBaseAssetId();
            long amount = (maker.getSide() == OrderSide.BUY) ? DecimalUtil.mulCeil(maker.getPrice(), unfreezeQty) : unfreezeQty;
            ledger.unfreezeBalance(maker.getUserId(), assetId, amount, gwSeq);
        }

        // 更新磁碟狀態與發送回報
        maker.setStatus((byte) 3); // CANCELED
        OrderBook.get(maker.getSymbolId()).syncOrder(maker, gwSeq);

        reporter.sendReport(maker.getUserId(), maker.getOrderId(), maker.getClientOrderId(), 
                OrderStatus.CANCELED, 0, 0, maker.getFilled(), 0, ctxTimestamp, gwSeq);

        log.info("STP 撤單處理完成: OrderId={}, UserId={}", maker.getOrderId(), maker.getUserId());
    }
    }

