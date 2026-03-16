package open.vincentf13.service.spot.matching.engine;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.*;
import open.vincentf13.service.spot.sbe.*;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 訂單處理核心邏輯 (Order Processor)
 * 職責：限價單建立、撮合觸發、資產凍結與解凍、撤單處理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderProcessor {
    private final ChronicleMap<Long, Order> orders = Storage.self().orders();
    private final ChronicleMap<CidKey, Long> clientOrderIdDiskMap = Storage.self().clientOrderIdMap();
    
    private final Ledger ledger;
    private final ExecutionReporter reporter;

    @PostConstruct
    public void init() { log.info("OrderProcessor 初始化完成..."); }

    public void coldStartRebuild() {
        log.warn("未檢測到有效內存快照，正在執行耗時的全量磁碟掃描以恢復狀態...");
        OrderBook.rebuildAll();
    }

    /** 核心入口：處理下單指令 */
    public void processCreateCommand(long userId, int symbolId, long price, long qty, Side side, long clientOrderId, long gatewaySequence, Supplier<Long> orderIdSupplier, LongSupplier tradeIdSupplier) {
        final ThreadContext context = ThreadContext.get();
        final CidKey cidKey = context.getRequestHolder().getCidKey(); 
        cidKey.set(userId, clientOrderId);

        if (clientOrderIdDiskMap.containsKey(cidKey)) return;

        handleOrderCreate(userId, symbolId, price, qty, side, clientOrderId, gatewaySequence, orderIdSupplier.get(), tradeIdSupplier);
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
        long unfreezeAmount = (order.getSide() == OrderSide.BUY) ? remainingQty * order.getPrice() : remainingQty;
        ledger.unfreezeBalance(order.getUserId(), assetId, unfreezeAmount, gatewaySequence);

        // 4. 更新狀態
        order.setStatus((byte) OrderStatus.CANCELED.ordinal());
        order.setLastSeq(gatewaySequence);
        orders.put(orderId, order);

        // 5. 發送回報
        reporter.reportCanceled(order);
    }

    private void handleOrderCreate(long userId, int symbolId, long price, long quantity, Side side, long clientOrderId, long gatewaySequence, long orderId, LongSupplier tradeIdSupplier) {
        // 1. 凍結資產
        OrderBook book = OrderBook.get(symbolId);
        int assetId = (side == Side.BUY) ? book.getQuoteAssetId() : book.getBaseAssetId(); 
        long freezeAmount = (side == Side.BUY) ? price * quantity : quantity;

        if (!ledger.freezeBalance(userId, assetId, freezeAmount, gatewaySequence)) {
            // --- 壓測優化：自動充值 (10 億單位) ---
            log.info("[TEST-FUNDING] 用戶 {} 資產不足，自動充入 1,000,000,000 單位資產 {}", userId, assetId);
            ledger.increaseAvailable(userId, assetId, 1_000_000_000L, gatewaySequence);
            if (!ledger.freezeBalance(userId, assetId, freezeAmount, gatewaySequence)) {
                reporter.reportRejected(userId, clientOrderId);
                return;
            }
        }

        // 2. 進入 OrderBook 撮合
        final byte takerSide = (byte) (side == Side.BUY ? OrderSide.BUY : OrderSide.SELL);
        book.handleCreate(orderId, userId, symbolId, price, quantity, side, clientOrderId, System.currentTimeMillis(), gatewaySequence, tradeIdSupplier::getAsLong, new OrderBook.TradeFinalizer() {
            @Override
            public void onMatch(long tradeId, Order maker, long tradePrice, long tradeQty, int baseAsset, int quoteAsset) {
                // 成交結算
                ledger.settleTrade(maker.getUserId(), userId, tradePrice, tradeQty, takerSide, price, gatewaySequence, baseAsset, quoteAsset, tradeId);
                
                // 寫入客戶端訂單 ID 映射 (持久化)
                CidKey makerCid = new CidKey();
                makerCid.set(maker.getUserId(), maker.getClientOrderId());
                clientOrderIdDiskMap.put(makerCid, maker.getOrderId());
            }
        });

        // 3. 寫入客戶端訂單 ID 映射 (持久化 taker)
        CidKey takerCid = new CidKey();
        takerCid.set(userId, clientOrderId);
        clientOrderIdDiskMap.put(takerCid, orderId);

        // 4. 發送回報 (Accepted)
        Order taker = orders.get(orderId);
        if (taker != null) {
            reporter.reportAccepted(taker);
        }
    }
}
