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
    
    private final OrderBook orderBook;
    private final Ledger ledger;
    private final ExecutionReporter reporter;

    @PostConstruct
    public void init() { log.info("OrderProcessor 初始化完成..."); }

    /** 核心入口：處理下單指令 */
    public void processCreateCommand(OrderCreateDecoder decoder, long gatewaySequence, Supplier<Long> orderIdSupplier, LongSupplier tradeIdSupplier) {
        final ThreadContext context = ThreadContext.get();
        final CidKey cidKey = context.getRequestHolder().getCidKey(); 
        cidKey.set(decoder.userId(), decoder.clientOrderId());

        if (clientOrderIdDiskMap.containsKey(cidKey)) return;

        handleOrderCreate(decoder, gatewaySequence, orderIdSupplier.get(), tradeIdSupplier);
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

    private void handleOrderCreate(OrderCreateDecoder decoder, long gatewaySequence, long orderId, LongSupplier tradeIdSupplier) {
        final long userId = decoder.userId();
        final int symbolId = decoder.symbolId();
        final long price = decoder.price();
        final long quantity = decoder.qty();
        final Side side = decoder.side();
        final long clientOrderId = decoder.clientOrderId();
        final long timestamp = decoder.timestamp();

        // 1. 凍結資產
        OrderBook book = OrderBook.get(symbolId);
        int assetId = (side == Side.BUY) ? book.getQuoteAssetId() : book.getBaseAssetId(); 
        long freezeAmount = (side == Side.BUY) ? price * quantity : quantity;

        if (!ledger.freezeBalance(userId, assetId, freezeAmount, gatewaySequence)) {
            reporter.reportRejected(userId, clientOrderId);
            return;
        }

        // 2. 進入 OrderBook 撮合
        book.handleCreate(orderId, decoder, clientOrderId, gatewaySequence, tradeIdSupplier::getAsLong, new OrderBook.TradeFinalizer() {
            @Override
            public void onMatch(long tradeId, Order maker, long price, long quantity, int baseAsset, int quoteAsset) {
                // 成交結算
                ledger.settleTrade(maker.getUserId(), userId, price, quantity, 
                        (byte)(side == Side.BUY ? OrderSide.BUY : OrderSide.SELL), price, gatewaySequence, baseAsset, quoteAsset, tradeId);
                
                // 發送 Maker 回報 (成交)
                reporter.reportTrade(maker, price, quantity);
            }
        });

        // 3. 獲取 Taker 最終狀態並發報
        final ThreadContext context = ThreadContext.get();
        Order taker = orders.getUsing(orderId, context.getReusableOrder());
        if (taker != null) {
            if (taker.getStatus() == OrderStatus.NEW.ordinal()) {
                reporter.reportAccepted(taker);
            } else {
                // Taker 的成交回報在撮合回調中會觸發，此處僅處理最終可能的掛單或結尾報告
                reporter.reportTrade(taker, 0, 0);
            }
        }
    }

    public void coldStartRebuild() {
        log.info("正在從 Chronicle Map 恢復 OrderBook 狀態...");
        orders.forEach((id, order) -> {
            if (order.getStatus() < OrderStatus.FILLED.ordinal()) {
                OrderBook.get(order.getSymbolId()).recoverOrder(order);
            }
        });
        log.info("OrderBook 狀態恢復完成。");
    }
}
