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
        // 預熱布隆過濾器，數據量大時建議異步，但在撮合引擎啟動階段同步是安全的
        clientOrderIdDiskMap.keySet().forEach(cidBloomFilter::put);
        log.info("布隆過濾器預熱完成。");
    }

    public void coldStartRebuild() {
        log.warn("未檢測到有效內存快照，正在執行耗時的全量磁碟掃描以恢復狀態...");
        OrderBook.rebuildActiveOrdersIndexes();
    }

    /** 核心入口：處理下單指令 */
    public void processCreateCommand(long userId, int symbolId, long price, long qty, Side side, long clientOrderId, long gatewaySequence, long timestamp, Supplier<Long> orderIdSupplier, LongSupplier tradeIdSupplier) {
        final ThreadContext context = ThreadContext.get();
        final CidKey cidKey = context.getRequestHolder().getCidKey(); 
        cidKey.set(userId, clientOrderId);

        // 1. 布隆過濾器快速判定：如果判定為絕對不存在於磁碟
        if (!cidBloomFilter.mightContain(cidKey)) {
            handleOrderCreate(userId, symbolId, price, qty, side, clientOrderId, gatewaySequence, timestamp, orderIdSupplier.get(), tradeIdSupplier);
            // 此處暫時不 put，在 handleOrderCreate 成功後再 put
            return;
        }

        // 2. 磁碟 Map 查詢：僅在布隆過濾器判定為 "可能存在" 時執行 (處理 False Positive)
        if (clientOrderIdDiskMap.containsKey(cidKey)) {
            log.debug("[IDEMPOTENCY] 攔截到重複訂單: uid={}, cid={}", userId, clientOrderId);
            return;
        }

        handleOrderCreate(userId, symbolId, price, qty, side, clientOrderId, gatewaySequence, timestamp, orderIdSupplier.get(), tradeIdSupplier);
    }

    /** 處理撤單指令 */
    public void processCancelCommand(long userId, long orderId, long gatewaySequence) {
        final ThreadContext context = ThreadContext.get();
        Order order = orders.getUsing(orderId, context.getReusableOrder());
        
        // 1. 基礎校驗
        if (order == null || order.getUserId() != userId || order.getStatus() >= OrderStatus.FILLED.ordinal()) {
            return;
        }

        // 2. 從 OrderBook 移除
        OrderBook book = OrderBook.get(order.getSymbolId());
        book.remove(orderId);

        // 3. 資產解凍
        int assetId = (order.getSide() == OrderSide.BUY) ? book.getQuoteAssetId() : book.getBaseAssetId();
        long remainingQty = order.getQty() - order.getFilled();
        long unfreezeAmount = (order.getSide() == OrderSide.BUY) ? DecimalUtil.mulFloor(order.getPrice(), remainingQty) : remainingQty;
        ledger.unfreezeBalance(order.getUserId(), assetId, unfreezeAmount, gatewaySequence);

        // 4. 更新狀態
        order.setStatus((byte) OrderStatus.CANCELED.ordinal());
        order.setLastSeq(gatewaySequence);
        orders.put(orderId, order);

        // 5. 發送回報
        reporter.reportCanceled(order);
    }

    /** 實作 TradeFinalizer 接口以複用 */
    @Override
    public void onMatch(long tradeId, Order maker, long tradePrice, long tradeQty, int baseAsset, int quoteAsset) {
        // 成交結算
        ledger.settleTrade(maker.getUserId(), currentTakerUserId, tradePrice, tradeQty, currentTakerSide, tradePrice, currentGatewaySequence, baseAsset, quoteAsset, tradeId);
    }

    private void handleOrderCreate(long userId, int symbolId, long price, long quantity, Side side, long clientOrderId, long gatewaySequence, long orderId, LongSupplier tradeIdSupplier) {
        // 1. 凍結資產
        OrderBook book = OrderBook.get(symbolId);
        int assetId = (side == Side.BUY) ? book.getQuoteAssetId() : book.getBaseAssetId(); 
        long freezeAmount = (side == Side.BUY) ? DecimalUtil.mulCeil(price, quantity) : quantity;

        if (!ledger.freezeBalance(userId, assetId, freezeAmount, gatewaySequence)) {
            // --- 壓測優化：自動充值 (10 億單位) ---
            log.debug("[TEST-FUNDING] 用戶 {} 資產 {} 不足，自動充值並重試", userId, assetId);
            ledger.increaseAvailable(userId, assetId, 1_000_000_000L, gatewaySequence);
            if (!ledger.freezeBalance(userId, assetId, freezeAmount, gatewaySequence)) {
                log.error("[ORDER-REJECT] 自動充值後凍結依然失敗: uid={}, asset={}", userId, assetId);
                reporter.reportRejected(userId, clientOrderId);
                return;
            }
        }

        // 2. 設置當前 Taker 上下文供 onMatch 使用
        this.currentTakerUserId = userId;
        this.currentTakerSide = (byte) (side == Side.BUY ? OrderSide.BUY : OrderSide.SELL);
        this.currentGatewaySequence = gatewaySequence;

        // 3. 進入 OrderBook 撮合
        book.handleCreate(orderId, userId, symbolId, price, quantity, side, clientOrderId, System.currentTimeMillis(), gatewaySequence, tradeIdSupplier::getAsLong, this);

        // 4. 寫入客戶端訂單 ID 映射 (持久化 taker) 與 更新布隆過濾器
        final CidKey takerCid = new CidKey(); // 必須新對象，因為會存入 Map 和 Filter
        takerCid.set(userId, clientOrderId);
        clientOrderIdDiskMap.put(takerCid, orderId);
        cidBloomFilter.put(takerCid);

        // 5. 發送回報 (Accepted)
        Order taker = orders.get(orderId);
        if (taker != null) {
            reporter.reportAccepted(taker);
        }
    }
}
