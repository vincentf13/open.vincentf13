package open.vincentf13.service.spot.matching.engine;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.util.DecimalUtil;
import open.vincentf13.service.spot.model.*;
import open.vincentf13.service.spot.sbe.*;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 訂單處理核心邏輯 (Order Processor) - 性能優化版
 * 職責：限價單建立、撮合觸發、資產凍結與解凍、撤單處理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderProcessor implements OrderBook.TradeFinalizer {
    private final ChronicleMap<Long, Order> orders = Storage.self().orders();
    private final ChronicleMap<CidKey, Long> clientOrderIdDiskMap = Storage.self().clientOrderIdMap();
    
    // --- 性能優化：冪等鍵寫緩衝 (零對象分配 Circular Buffer 版) ---
    private static final int BUFFER_SIZE = 4096;
    private final long[] pendingUids = new long[BUFFER_SIZE];
    private final long[] pendingCidsArr = new long[BUFFER_SIZE];
    private final long[] pendingOids = new long[BUFFER_SIZE];
    private int bufferCount = 0;
    
    // --- 性能優化：內存布隆過濾器 (減少磁碟 I/O 停頓) ---
    private final com.google.common.hash.BloomFilter<CidKey> cidBloomFilter = com.google.common.hash.BloomFilter.create(
            (from, into) -> into.putLong(from.getUserId()).putLong(from.getClientOrderId()),
            2_000_000, 0.001);

    private final Ledger ledger;
    private final ExecutionReporter reporter;

    private long currentGatewaySequence;
    private long currentTakerUserId;
    private byte currentTakerSide;

    @PostConstruct
    public void init() { 
        log.info("OrderProcessor 初始化完成，正在預熱布隆過濾器..."); 
        clientOrderIdDiskMap.keySet().forEach(cidBloomFilter::put);
        log.info("布隆過濾器預熱完成。");
    }

    /** 核心落地：將緩衝的冪等鍵批量寫入磁碟，並歸還 CidKey 物件池 */
    public void flush() {
        if (bufferCount > 0) {
            CidKey reusable = new CidKey();
            for (int i = 0; i < bufferCount; i++) {
                reusable.set(pendingUids[i], pendingCidsArr[i]);
                clientOrderIdDiskMap.put(reusable, pendingOids[i]);
            }
            bufferCount = 0;
        }
    }

    public void coldStartRebuild() {
        log.warn("未檢測到有效內存快照，正在執行耗時的全量磁碟掃描以恢復狀態...");
        OrderBook.rebuildActiveOrdersIndexes();
    }

    /** 核心入口：處理下單指令 */
    public void processCreateCommand(long userId, int symbolId, long price, long qty, Side side, long clientOrderId, long gatewaySequence, long timestamp, 
                                   open.vincentf13.service.spot.model.WalProgress progress) {
        final ThreadContext context = ThreadContext.get();
        final CidKey cidKey = context.getCidKey(); 
        cidKey.set(userId, clientOrderId);

        // 1. 檢查當前記憶體緩衝區 (10ms 內的最熱點，防重複下單)
        for (int i = 0; i < bufferCount; i++) {
            if (pendingUids[i] == userId && pendingCidsArr[i] == clientOrderId) return;
        }

        // 2. 布隆過濾器快速判定
        if (!cidBloomFilter.mightContain(cidKey)) {
            handleOrderCreate(userId, symbolId, price, qty, side, clientOrderId, gatewaySequence, timestamp, progress.getAndIncrOrderId(), progress);
            return;
        }

        // 3. 磁碟 Map 查詢 (僅在布隆過濾器判定為 "可能存在" 時執行)
        if (clientOrderIdDiskMap.containsKey(cidKey)) return;

        handleOrderCreate(userId, symbolId, price, qty, side, clientOrderId, gatewaySequence, timestamp, progress.getAndIncrOrderId(), progress);
    }

    /** 處理撤單指令 */
    public void processCancelCommand(long userId, long orderId, long gatewaySequence) {
        final ThreadContext context = ThreadContext.get();
        Order order = orders.getUsing(orderId, context.getReusableOrder());
        
        if (order == null || order.getUserId() != userId || order.getStatus() >= OrderStatus.FILLED.ordinal()) return;

        OrderBook book = OrderBook.get(order.getSymbolId());
        book.remove(orderId);

        int assetId = (order.getSide() == OrderSide.BUY) ? book.getQuoteAssetId() : book.getBaseAssetId();
        long remainingQty = order.getQty() - order.getFilled();
        long unfreezeAmount = (order.getSide() == OrderSide.BUY) ? DecimalUtil.mulFloor(order.getPrice(), remainingQty) : remainingQty;
        ledger.unfreezeBalance(order.getUserId(), assetId, unfreezeAmount, gatewaySequence);

        order.setStatus((byte) OrderStatus.CANCELED.ordinal());
        order.setLastSeq(gatewaySequence);
        orders.put(orderId, order);
        reporter.reportCanceled(order);
    }

    /** 實作 TradeFinalizer 接口以複用 */
    @Override
    public void onMatch(long tradeId, Order maker, long tradePrice, long tradeQty, int baseAsset, int quoteAsset) {
        ledger.settleTrade(maker.getUserId(), currentTakerUserId, tradePrice, tradeQty, currentTakerSide, tradePrice, currentGatewaySequence, baseAsset, quoteAsset, tradeId);
    }

    private void handleOrderCreate(long userId, int symbolId, long price, long quantity, Side side, long clientOrderId, long gatewaySequence, long timestamp, long orderId, 
                                 open.vincentf13.service.spot.model.WalProgress progress) {
        final ThreadContext context = ThreadContext.get();
        OrderBook book = OrderBook.get(symbolId);
        int assetId = (side == Side.BUY) ? book.getQuoteAssetId() : book.getBaseAssetId(); 
        long freezeAmount = (side == Side.BUY) ? DecimalUtil.mulCeil(price, quantity) : quantity;

        if (!ledger.freezeBalance(userId, assetId, freezeAmount, gatewaySequence)) {
            reporter.reportRejected(userId, clientOrderId);
            return;
        }

        this.currentTakerUserId = userId;
        this.currentTakerSide = (byte) (side == Side.BUY ? OrderSide.BUY : OrderSide.SELL);
        this.currentGatewaySequence = gatewaySequence;

        // 3. 進入 OrderBook 撮合
        Order taker = book.handleCreate(orderId, userId, symbolId, price, quantity, side, clientOrderId, timestamp, gatewaySequence, progress, this);

        // 4. 寫入客戶端訂單 ID 映射緩衝 與 更新布隆過濾器
        if (bufferCount < BUFFER_SIZE) {
            pendingUids[bufferCount] = userId;
            pendingCidsArr[bufferCount] = clientOrderId;
            pendingOids[bufferCount] = orderId;
            bufferCount++;
        }
        
        final CidKey takerCid = context.getCidKey();
        takerCid.set(userId, clientOrderId);
        cidBloomFilter.put(takerCid);

        if (taker != null) reporter.reportAccepted(taker);
    }
}
