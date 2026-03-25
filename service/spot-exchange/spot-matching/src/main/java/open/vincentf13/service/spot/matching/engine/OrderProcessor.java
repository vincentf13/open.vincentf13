package open.vincentf13.service.spot.matching.engine;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.thread.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.util.DecimalUtil;
import open.vincentf13.service.spot.model.CidKey;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.infra.Constants.OrderSide;
import open.vincentf13.service.spot.sbe.OrderStatus;
import open.vincentf13.service.spot.sbe.Side;
import open.vincentf13.service.spot.model.command.OrderCreateCommand;
import open.vincentf13.service.spot.sbe.OrderCreateDecoder;
import org.springframework.stereotype.Component;

/**
 * 訂單處理器 (Zero-GC 優化版)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderProcessor implements OrderBook.TradeFinalizer {
    private final ChronicleMap<open.vincentf13.service.spot.infra.chronicle.LongValue, Order> orders = Storage.self().orders();
    private final ChronicleMap<CidKey, open.vincentf13.service.spot.infra.chronicle.LongValue> clientOrderIdDiskMap = Storage.self().clientOrderIdMap();

    // --- 性能優化：冪等鍵寫緩衝 (零對象分配 Circular Buffer 版) ---
    private static final int BUFFER_SIZE = 4096;
    private final long[] pendingUids = new long[BUFFER_SIZE];
    private final long[] pendingCidsArr = new long[BUFFER_SIZE];
    private final long[] pendingOids = new long[BUFFER_SIZE];
    private int bufferCount = 0;

    private final Ledger ledger;
    private final ExecutionReporter reporter;

    private final CidKey reusableFlushKey = new CidKey();
    private final open.vincentf13.service.spot.infra.chronicle.LongValue reusableFlushValue = new open.vincentf13.service.spot.infra.chronicle.LongValue();
    private final open.vincentf13.service.spot.infra.chronicle.LongValue cancelKey = new open.vincentf13.service.spot.infra.chronicle.LongValue();
    private long currentGatewaySequence;
    private long currentTakerUserId;
    private long currentTakerPrice;
    private byte currentTakerSide;

    @PostConstruct
    public void init() { 
        log.info("OrderProcessor 初始化完成。"); 
    }

    /** 核心落地：將緩衝的冪等鍵批量寫入磁碟，實現零分配 */
    public void flush() {
        if (bufferCount > 0) {
            for (int i = 0; i < bufferCount; i++) {
                reusableFlushKey.set(pendingUids[i], pendingCidsArr[i]);
                reusableFlushValue.set(pendingOids[i]);
                clientOrderIdDiskMap.put(reusableFlushKey, reusableFlushValue);
            }
            bufferCount = 0;
        }
    }

    public void coldStartRebuild() {
        log.warn("未檢測到有效內存快照，正在執行耗時的全量磁碟掃描以恢復狀態...");
        OrderBook.rebuildActiveOrdersIndexes();
    }

    /** 核心入口：處理下單指令 */
    public void processCreateCommand(long userId, int symbolId, long price, long qty, Side side, long clientOrderId, long gatewaySequence, long timestamp, open.vincentf13.service.spot.model.WalProgress progress) {
        final ThreadContext context = ThreadContext.get();
        final CidKey cidKey = context.getCidKey();
        cidKey.set(userId, clientOrderId);

        // 1. 三道防線攔截重複指令：緩衝區 -> 磁碟 Map (零分配查詢)
        boolean duplicate = false;
        for (int i = 0; i < bufferCount; i++) {
            if (pendingUids[i] == userId && pendingCidsArr[i] == clientOrderId) { duplicate = true; break; }
        }
        
        if (duplicate) return;
        if (clientOrderIdDiskMap.containsKey(cidKey)) return;

        // 2. 確定不重複後，執行下單
        handleOrderCreate(userId, symbolId, price, qty, side, clientOrderId, gatewaySequence, timestamp, progress.getAndIncrOrderId(), progress);
    }

    /** 處理撤單指令 */
    public void processCancelCommand(long userId, long orderId, long gatewaySequence) {
        final ThreadContext context = ThreadContext.get();
        cancelKey.set(orderId);
        Order order = orders.getUsing(cancelKey, context.getReusableOrder());
        
        if (order == null || order.getUserId() != userId || order.getStatus() >= OrderStatus.FILLED.ordinal()) return;

        OrderBook book = OrderBook.get(order.getSymbolId());
        book.remove(orderId);

        int assetId = (order.getSide() == OrderSide.BUY) ? book.getQuoteAssetId() : book.getBaseAssetId();
        // 直接使用 order 中剩餘的 frozen 金額，確保 100% 準確
        ledger.unfreezeBalance(order.getUserId(), assetId, order.getFrozen(), gatewaySequence);

        order.setStatus((byte) OrderStatus.CANCELED.ordinal());
        order.setLastSeq(gatewaySequence);
        order.setFrozen(0); // 撤單後歸零
        orders.put(cancelKey, order);
        reporter.reportCanceled(order);
    }

    /** 實作 TradeFinalizer 接口以複用 */
    @Override
    public void onMatch(long tradeId, Order maker, Order taker, long tradePrice, long tradeQty, int baseAsset, int quoteAsset) {
        long mFrozenDelta, tFrozenDelta;
        
        if (maker.getSide() == OrderSide.BUY) {
            // Maker 是買方：計算此次成交應釋放的凍結金額
            long remainingQty = maker.getQty() - maker.getFilled(); // 注意：此時 filled 已包含此次成交量
            long nextFrozen = DecimalUtil.mulCeil(maker.getPrice(), remainingQty);
            mFrozenDelta = maker.getFrozen() - nextFrozen;
            maker.setFrozen(nextFrozen);
        } else {
            // Maker 是賣方：凍結的是 Base， delta 就是 tradeQty
            mFrozenDelta = tradeQty;
            maker.setFrozen(maker.getFrozen() - tradeQty);
        }

        if (taker.getSide() == OrderSide.BUY) {
            // Taker 是買方
            long remainingQty = taker.getQty() - taker.getFilled();
            long nextFrozen = DecimalUtil.mulCeil(taker.getPrice(), remainingQty);
            tFrozenDelta = taker.getFrozen() - nextFrozen;
            taker.setFrozen(nextFrozen);
        } else {
            // Taker 是賣方
            tFrozenDelta = tradeQty;
            taker.setFrozen(taker.getFrozen() - tradeQty);
        }

        ledger.settleTrade(maker.getUserId(), taker.getUserId(), tradePrice, tradeQty, taker.getSide(), mFrozenDelta, tFrozenDelta, currentGatewaySequence, baseAsset, quoteAsset, tradeId);
    }

    private void handleOrderCreate(long userId, int symbolId, long price, long quantity, Side side, long clientOrderId, long gatewaySequence, long timestamp, long orderId, 
                                 open.vincentf13.service.spot.model.WalProgress progress) {
        OrderBook book = OrderBook.get(symbolId);
        int assetId = (side == Side.BUY) ? book.getQuoteAssetId() : book.getBaseAssetId(); 
        long freezeAmount = (side == Side.BUY) ? DecimalUtil.mulCeil(price, quantity) : quantity;

        if (!ledger.freezeBalance(userId, assetId, freezeAmount, gatewaySequence)) {
            reporter.reportRejected(userId, clientOrderId);
            return;
        }

        this.currentTakerUserId = userId;
        this.currentTakerPrice = price;
        this.currentTakerSide = (byte) (side == Side.BUY ? OrderSide.BUY : OrderSide.SELL);
        this.currentGatewaySequence = gatewaySequence;

        // 3. 進入 OrderBook 撮合
        Order taker = book.handleCreate(orderId, userId, symbolId, price, quantity, side, clientOrderId, timestamp, gatewaySequence, progress, this);
        if (taker != null) {
            taker.setFrozen(freezeAmount);
        }

        // 4. 寫入客戶端訂單 ID 映射緩衝
        if (bufferCount < BUFFER_SIZE) {
            pendingUids[bufferCount] = userId;
            pendingCidsArr[bufferCount] = clientOrderId;
            pendingOids[bufferCount] = orderId;
            bufferCount++;
        }

        if (taker != null) reporter.reportAccepted(taker);
    }
}
