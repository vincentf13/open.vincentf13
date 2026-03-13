package open.vincentf13.service.spot.matching.engine;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
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

    private final CidKey reusableCidKey = new CidKey();

    @PostConstruct
    public void init() { log.info("OrderProcessor 初始化完成..."); }

    /** 核心入口：處理下單指令 */
    public void processCreateCommand(OrderCreateDecoder decoder, long gatewaySequence, Supplier<Long> orderIdSupplier, LongSupplier tradeIdSupplier) {
        // 1. 冪等性檢查 (Client Order ID)
        reusableCidKey.set(decoder.userId(), decoder.clientOrderId());

        if (clientOrderIdDiskMap.containsKey(reusableCidKey)) return;

        handleOrderCreate(decoder, gatewaySequence, orderIdSupplier.get(), tradeIdSupplier);
    }

    /** 處理撤單指令 */
    public void processCancelCommand(long userId, long orderId, long gatewaySequence) {
        Order order = orders.get(orderId);
        
        // 1. 基礎校驗
        if (order == null || order.getUserId() != userId || order.getStatus() >= OrderStatus.FILLED.ordinal()) {
            return;
        }

        // 2. 從 OrderBook 移除 (內部會釋放物件)
        OrderBook.get(order.getSymbolId()).remove(orderId);

        // 3. 資產解凍
        long freezeQuantity = (order.getSide() == OrderSide.BUY) 
                ? order.getQty() * order.getPrice() 
                : order.getQty();
        ledger.unfreezeBalance(order.getUserId(), (order.getSide() == OrderSide.BUY) ? (order.getSymbolId() / 1000) : (order.getSymbolId() % 100), freezeQuantity, gatewaySequence);

        // 4. 更新狀態
        order.setStatus((byte) OrderStatus.CANCELLED.ordinal());
        order.setLastSeq(gatewaySequence);
        orders.put(orderId, order);

        // 5. 發送回報
        reporter.reportCanceled(order.getUserId(), order.getOrderId(), order.getClientOrderId(), 
                order.getFilled(), System.currentTimeMillis(), gatewaySequence);
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
                
                // 發送 Maker 回報
                reporter.reportMatched(maker.getUserId(), maker.getOrderId(), maker.getClientOrderId(),
                        maker.getFilled() == maker.getQty() ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED,
                        price, quantity, maker.getFilled(), 0, timestamp, gatewaySequence);
            }

            @Override
            public void onSTP(Order maker, long gatewaySequence) {
                // 自成交預防：解凍 Maker 並發送撤單回報
                long makerFreezeQuantity = (maker.getSide() == OrderSide.BUY) ? maker.getQty() * maker.getPrice() : maker.getQty();
                int makerAssetId = (maker.getSide() == OrderSide.BUY) ? (maker.getSymbolId() / 1000) : (maker.getSymbolId() % 100);
                ledger.unfreezeBalance(maker.getUserId(), makerAssetId, makerFreezeQuantity, gatewaySequence);
                
                maker.setStatus((byte) OrderStatus.CANCELLED.ordinal());
                orders.put(maker.getOrderId(), maker);
                
                reporter.reportCanceled(maker.getUserId(), maker.getOrderId(), maker.getClientOrderId(),
                        maker.getFilled(), timestamp, gatewaySequence);
            }
        });

        // 3. 獲取 Taker 最終狀態並發報
        Order taker = orders.get(orderId);
        if (taker != null) {
            reporter.reportMatched(userId, orderId, clientOrderId, 
                    OrderStatus.values()[taker.getStatus()], 0, 0, taker.getFilled(), 0, timestamp, gatewaySequence);
        } else {
            // 掛單成功但物件已被回收 (通常由 handleCreate 內部回調處理，此處為嚴謹性補充)
            Order savedTaker = orders.get(orderId);
            if (savedTaker != null && savedTaker.getStatus() == OrderStatus.NEW.ordinal()) {
                reporter.reportAccepted(userId, orderId, clientOrderId, timestamp, gatewaySequence);
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
