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

        // 2. 從 OrderBook 移除 (內部會釋放物件)
        OrderBook.get(order.getSymbolId()).remove(orderId);

        // 3. 資產解凍
        long freezeQuantity = (order.getSide() == OrderSide.BUY) ? order.getQty() * order.getPrice() : order.getQty();
        ledger.unfreezeBalance(order.getUserId(), (order.getSide() == OrderSide.BUY) ? (order.getSymbolId() / 1000) : (order.getSymbolId() % 100), freezeQuantity, gatewaySequence);

        // 4. 更新狀態
        order.setStatus((byte) OrderStatus.CANCELED.ordinal());
        order.setLastSeq(gatewaySequence);
        orders.put(orderId, order);

        // 5. 發送回報 (自動從物件提取狀態)
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
        int assetId = (side == Side.BUY) ? (symbolId / 1000) : (symbolId % 100); 
        long freezeAmount = (side == Side.BUY) ? price * quantity : quantity;

        if (!ledger.freezeBalance(userId, assetId, freezeAmount, gatewaySequence)) {
            reporter.reportRejected(userId, clientOrderId, timestamp, gatewaySequence);
            return;
        }

        // 2. 進入 OrderBook 撮合
        OrderBook book = OrderBook.get(symbolId);
        book.handleCreate(orderId, decoder, clientOrderId, gatewaySequence, tradeIdSupplier::getAsLong, new OrderBook.TradeFinalizer() {
            @Override
            public void onMatch(long tradeId, Order maker, long price, long quantity, int baseAsset, int quoteAsset) {
                // 成交結算
                ledger.settleTrade(maker.getUserId(), userId, price, quantity, 
                        (byte)(side == Side.BUY ? OrderSide.BUY : OrderSide.SELL), price, gatewaySequence, baseAsset, quoteAsset, tradeId);
                
                // 發送 Maker 回報 (自動提取狀態)
                reporter.reportMatched(maker, price, quantity);
            }

            @Override
            public void onSTP(Order maker, long gatewaySequence) {
                // 自成交預防
                long makerFreezeQuantity = (maker.getSide() == OrderSide.BUY) ? maker.getQty() * maker.getPrice() : maker.getQty();
                int makerAssetId = (maker.getSide() == OrderSide.BUY) ? (maker.getSymbolId() / 1000) : (maker.getSymbolId() % 100);
                ledger.unfreezeBalance(maker.getUserId(), makerAssetId, makerFreezeQuantity, gatewaySequence);
                
                maker.setStatus((byte) OrderStatus.CANCELED.ordinal());
                orders.put(maker.getOrderId(), maker);
                
                reporter.reportCanceled(maker);
            }
        });

        // 3. 獲取 Taker 最終狀態並發報 (極簡化：委託給 Reporter 判定分支)
        final ThreadContext context = ThreadContext.get();
        Order taker = orders.getUsing(orderId, context.getReusableOrder());
        if (taker != null) {
            reporter.reportTakerFinalState(taker);
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
