package open.vincentf13.service.spot_exchange.core;

import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import net.openhft.chronicle.queue.ExcerptTailer;
import open.vincentf13.service.spot_exchange.model.ActiveOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 
  撮合引擎核心邏輯單元 (Logic Unit)
  單線程忙等 (Busy-spin) 處理指令
 */
@Component
public class MatchingEngine implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(MatchingEngine.class);

    private final InboundSequencer inbound;
    private final OutboundSequencer outbound;
    private final StateStore stateStore;
    
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executor;

    private final LedgerProcessor ledger;
    private final Map<Integer, OrderBook> books = new HashMap<>();
    private long orderIdCounter = 1;

    private ChronicleQueue coreQueue;

    public MatchingEngine(InboundSequencer inbound, OutboundSequencer outbound, 
                         StateStore stateStore, LedgerProcessor ledger) {
        this.inbound = inbound;
        this.outbound = outbound;
        this.stateStore = stateStore;
        this.ledger = ledger;
        this.coreQueue = SingleChronicleQueueBuilder.binary("data/spot_exchange/core-queue").build();
    }

    @Override
    public void run() {
        ExcerptTailer tailer = coreQueue.createTailer();
        log.info("撮合引擎啟動，開始輪詢 Core WAL...");

        while (running.get()) {
            boolean handled = tailer.readDocument(wire -> {
                long timestamp = wire.read("timestamp").int64();
                int msgType = wire.read("msgType").int32();
                
                switch (msgType) {
                    case 103: // Auth
                        handleAuth(wire);
                        break;
                    case 100: // OrderCreate
                        handleOrderCreate(wire, timestamp);
                        break;
                    case 102: // Deposit
                        handleDeposit(wire);
                        break;
                    default:
                        log.warn("未知指令類型: {}", msgType);
                }
            });

            if (!handled) {
                Thread.onSpinWait(); 
            }
        }
    }

    private void handleAuth(net.openhft.chronicle.wire.WireIn wire) {
        long userId = wire.read("userId").int64();
        // 初始化 BTC (1) 與 USDT (2) 餘額
        ledger.getOrCreateBalance(userId, 1); 
        ledger.getOrCreateBalance(userId, 2);
        
        sendAuthSuccess(userId);
    }

    private void handleDeposit(net.openhft.chronicle.wire.WireIn wire) {
        long userId = wire.read("userId").int64();
        int assetId = wire.read("assetId").int32();
        long amount = wire.read("amount").int64();
        
        ledger.addAvailable(userId, assetId, amount);
        sendBalanceUpdate(userId);
    }

    private void handleOrderCreate(net.openhft.chronicle.wire.WireIn wire, long timestamp) {
        long userId = wire.read("userId").int64();
        int symbolId = wire.read("symbolId").int32();
        long price = wire.read("price").int64();
        long qty = wire.read("qty").int64();
        byte side = wire.read("side").int8();

        // 基礎風控: 凍結資產 (簡化: 假設 symbolId 1 是 BTC_USDT, 賣方扣 BTC, 買方扣 USDT)
        long cost = (side == 0) ? (price * qty / 100_000_000L) : qty;
        int assetToFreeze = (side == 0) ? 2 : 1; // 買方扣 USDT (2), 賣方扣 BTC (1)

        if (!ledger.tryFreeze(userId, assetToFreeze, cost)) {
            sendExecutionReport(userId, 0, "REJECTED_INSUFFICIENT_BALANCE");
            return;
        }

        ActiveOrder order = new ActiveOrder();
        order.setUserId(userId);
        order.setSymbolId(symbolId);
        order.setPrice(price);
        order.setQty(qty);
        order.setSide(side);
        order.setTimestamp(timestamp);
        order.setStatus((byte)0); // NEW

        // 執行撮合 (MVP 簡化: 直接放入 Book)
        OrderBook book = books.computeIfAbsent(symbolId, OrderBook::new);
        book.add(order);
        
        sendExecutionReport(userId, orderIdCounter++, "ACCEPTED");
        sendBalanceUpdate(userId);
    }

    private void sendAuthSuccess(long userId) {
        outbound.getQueue().acquireAppender().writeDocument(wire -> {
            wire.write("topic").text("auth.success");
            wire.write("userId").int64(userId);
        });
    }

    private void sendBalanceUpdate(long userId) {
        outbound.getQueue().acquireAppender().writeDocument(wire -> {
            wire.write("topic").text("balance");
            wire.write("userId").int64(userId);
        });
    }

    private void sendExecutionReport(long userId, long orderId, String status) {
        outbound.getQueue().acquireAppender().writeDocument(wire -> {
            wire.write("topic").text("execution");
            wire.write("userId").int64(userId);
            wire.write("orderId").int64(orderId);
            wire.write("status").text(status);
        });
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (executor != null) {
            executor.shutdown();
        }
    }
}
