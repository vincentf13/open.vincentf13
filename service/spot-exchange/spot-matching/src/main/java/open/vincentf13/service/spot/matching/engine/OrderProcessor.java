package open.vincentf13.service.spot.matching.engine;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.PointerBytesStore;
import net.openhft.chronicle.map.ChronicleMap;
import open.vincentf13.service.spot.infra.alloc.ThreadContext;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.alloc.SbeCodec;
import open.vincentf13.service.spot.model.*;
import open.vincentf13.service.spot.sbe.*;
import org.springframework.stereotype.Component;

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
    private final AssetProcessor assetProcessor;
    private final ExecutionReporter reporter;

    private final CidKey reusableCidKey = new CidKey();

    @PostConstruct
    public void init() { log.info("OrderProcessor 初始化完成..."); }

    /** 核心入口：處理下單指令 */
    public void processCreateCommand(PointerBytesStore payload, long gwSeq, Supplier<Long> orderIdSupplier, LongSupplier tradeIdSupplier) {
        OrderCreateDecoder decoder = SbeCodec.decodeOrderCreate(payload);
        
        long ctxTimestamp = decoder.timestamp();
        
        // 1. 冪等性檢查 (Client Order ID)
        reusableCidKey.set(decoder.userId(), decoder.clientOrderId());

        if (clientOrderIdDiskMap.containsKey(reusableCidKey)) return;

        handleOrderCreate(decoder, gwSeq, orderIdSupplier.get(), tradeIdSupplier);
    }

    /** 處理撤單指令 */
    public void processCancelCommand(long userId, long orderId, long gwSeq) {
        Order order = orders.get(orderId);
        
        // 1. 基礎校驗
        if (order == null || order.getUserId() != userId || order.getStatus() != OrderStatus.NEW.ordinal()) {
            return;
        }

        // 2. 從 OrderBook 移除
        orderBook.remove(order);

        // 3. 資產解凍
        long freezeQty = (order.getSide() == Side.BUY.ordinal()) 
                ? order.getQty() * order.getPrice() 
                : order.getQty();
        assetProcessor.unfreeze(order.getUserId(), order.getAssetId(), freezeQty);

        // 4. 更新狀態
        order.setStatus((byte) OrderStatus.CANCELED.ordinal());
        order.setLastSeq(gwSeq);
        orders.put(orderId, order);

        // 5. 發送回報
        reporter.sendReport(order.getUserId(), order.getOrderId(), order.getClientOrderId(), 
                OrderStatus.CANCELED, 0, 0, order.getCumQty(), order.getAvgPrice(), System.currentTimeMillis(), gwSeq);
    }

    private void handleOrderCreate(OrderCreateDecoder decoder, long gwSeq, long orderId, LongSupplier tradeIdSupplier) {
        final long userId = decoder.userId();
        final int symbolId = decoder.symbolId();
        final long price = decoder.price();
        final long qty = decoder.qty();
        final Side side = decoder.side();
        final long cid = decoder.clientOrderId();
        final long ts = decoder.timestamp();

        // 1. 凍結資產
        int assetId = side == Side.BUY ? (symbolId / 1000) : (symbolId % 100); 
        long freezeAmt = (side == Side.BUY) ? price * qty : qty;

        if (!assetProcessor.freeze(userId, assetId, freezeAmt)) {
            reporter.sendReport(userId, 0, cid, OrderStatus.REJECTED, 0, 0, 0, 0, ts, gwSeq);
            return;
        }

        // 2. 建立訂單物件
        Order taker = new Order(); 
        taker.fill(orderId, userId, symbolId, price, qty, (byte) side.ordinal(), ts, cid);
        taker.setLastSeq(gwSeq);

        // 3. 進入 OrderBook 撮合
        orderBook.match(taker, (maker, tradePrice, tradeQty) -> {
            long tradeId = tradeIdSupplier.getAsLong();
            processTrade(taker, maker, tradePrice, tradeQty, tradeId, ts, gwSeq);
        });

        // 4. 如果沒成交完，進入 OrderBook 掛單
        if (taker.getQty() > 0) {
            orderBook.add(taker);
            orders.put(orderId, taker);
            clientOrderIdDiskMap.put(new CidKey(userId, cid), orderId);
            reporter.sendReport(userId, orderId, cid, OrderStatus.NEW, 0, 0, 0, 0, ts, gwSeq);
        }
    }

    private void processTrade(Order taker, Order maker, long price, long qty, long tradeId, long ts, long gwSeq) {
        assetProcessor.move(taker.getUserId(), maker.getUserId(), taker.getAssetId(), maker.getAssetId(), price, qty);

        maker.setQty(maker.getQty() - qty);
        maker.setCumQty(maker.getCumQty() + qty);
        maker.setStatus((byte) (maker.getQty() == 0 ? OrderStatus.FILLED.ordinal() : OrderStatus.PARTIALLY_FILLED.ordinal()));
        orders.put(maker.getOrderId(), maker);

        taker.setQty(taker.getQty() - qty);
        taker.setCumQty(taker.getCumQty() + qty);
        taker.setStatus((byte) (taker.getQty() == 0 ? OrderStatus.FILLED.ordinal() : OrderStatus.PARTIALLY_FILLED.ordinal()));

        reporter.sendReport(maker.getUserId(), maker.getOrderId(), maker.getClientOrderId(), 
                OrderStatus.valueOf(maker.getStatus()), price, qty, maker.getCumQty(), maker.getAvgPrice(), ts, gwSeq);
        
        reporter.sendReport(taker.getUserId(), taker.getOrderId(), taker.getClientOrderId(), 
                OrderStatus.valueOf(taker.getStatus()), price, qty, taker.getCumQty(), taker.getAvgPrice(), ts, gwSeq);
    }

    public void coldStartRebuild() {
        log.info("正在從 Chronicle Map 恢復 OrderBook 狀態...");
        orders.forEach((id, order) -> {
            if (order.getStatus() == OrderStatus.NEW.ordinal() || order.getStatus() == OrderStatus.PARTIALLY_FILLED.ordinal()) {
                orderBook.add(order);
            }
        });
        log.info("OrderBook 狀態恢復完成。");
    }
}
