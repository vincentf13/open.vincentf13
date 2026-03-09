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
import open.vincentf13.service.spot_exchange.sbe.OrderCreateDecoder;
import open.vincentf13.service.spot_exchange.sbe.MessageHeaderDecoder;
import open.vincentf13.service.spot_exchange.sbe.Side;

@Component
public class MatchingEngine implements Runnable {
    // ... 前面代碼 ...
    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final OrderCreateDecoder orderCreateDecoder = new OrderCreateDecoder();
    private final UnsafeBuffer payloadBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(256));

    @Override
    public void run() {
        ExcerptTailer tailer = coreQueue.createTailer();
        log.info("撮合引擎啟動，開始輪詢 Core WAL...");

        while (running.get()) {
            boolean handled = tailer.readDocument(wire -> {
                int msgType = wire.read("msgType").int32();
                
                switch (msgType) {
                    case 103: // Auth
                        handleAuth(wire);
                        break;
                    case 100: // OrderCreate (SBE Template ID)
                        byte[] bytes = wire.read("payload").bytes();
                        payloadBuffer.putBytes(0, bytes);
                        
                        // SBE 解碼: 先解析 Header，再解析 Body
                        headerDecoder.wrap(payloadBuffer, 0);
                        orderCreateDecoder.wrap(payloadBuffer, 
                                               MessageHeaderDecoder.ENCODED_LENGTH, 
                                               headerDecoder.blockLength(), 
                                               headerDecoder.version());
                        
                        handleOrderCreate(orderCreateDecoder);
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

    private void handleOrderCreate(OrderCreateDecoder sbe) {
        long userId = sbe.userId();
        long price = sbe.price();
        long qty = sbe.qty();
        Side side = sbe.side();

        // 風控邏輯
        long cost = (side == Side.BUY) ? (price * qty / 100_000_000L) : qty;
        int assetToFreeze = (side == Side.BUY) ? 2 : 1;

        if (!ledger.tryFreeze(userId, assetToFreeze, cost)) {
            sendExecutionReport(userId, 0, "REJECTED_INSUFFICIENT_BALANCE");
            return;
        }

        // ... 撮合邏輯 ...
        sendExecutionReport(userId, orderIdCounter++, "ACCEPTED");
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
