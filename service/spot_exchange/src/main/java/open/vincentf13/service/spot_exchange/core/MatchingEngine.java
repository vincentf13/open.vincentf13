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

import open.vincentf13.service.spot_exchange.sbe.*;

@Component
public class MatchingEngine implements Runnable {
    // ... 前面已有的欄位 ...
    private final ExecutionReportEncoder executionEncoder = new ExecutionReportEncoder();
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final UnsafeBuffer outboundBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(512));

    private void handleOrderCreate(OrderCreateDecoder sbe) {
        long userId = sbe.userId();
        long price = sbe.price();
        long qty = sbe.qty();
        Side side = sbe.side();
        long symbolId = sbe.symbolId();

        // 1. 風控凍結
        long cost = (side == Side.BUY) ? (price * qty / 100_000_000L) : qty;
        int assetToFreeze = (side == Side.BUY) ? 2 : 1; // 簡化: 1=BTC, 2=USDT

        if (!ledger.tryFreeze(userId, assetToFreeze, cost)) {
            sendExecutionReport(userId, 0, sbe.getClientOrderId(), OrderStatus.REJECTED, 0, 0, 0, 0);
            return;
        }

        // 2. 建立訂單對象
        long orderId = orderIdCounter++;
        ActiveOrder order = new ActiveOrder();
        order.setOrderId(orderId);
        order.setUserId(userId);
        order.setPrice(price);
        order.setQty(qty);
        order.setSide((byte)(side == Side.BUY ? 0 : 1));

        // 3. 執行撮合
        OrderBook book = books.computeIfAbsent((int)symbolId, OrderBook::new);
        List<OrderBook.TradeEvent> trades = book.match(order);

        // 4. 處理成交帳務
        for (OrderBook.TradeEvent t : trades) {
            processTradeLedger(t, (int)symbolId);
            // 推送 Maker 的成交回報 (簡化版)
            sendExecutionReport(t.makerUserId, t.makerOrderId, "", OrderStatus.PARTIALLY_FILLED, t.price, t.qty, 0, 0);
        }

        // 5. 推送 Taker 的最終回報
        OrderStatus finalStatus = (order.getFilled() == order.getQty()) ? OrderStatus.FILLED : OrderStatus.NEW;
        sendExecutionReport(userId, orderId, sbe.getClientOrderId(), finalStatus, 0, 0, order.getFilled(), 0);
    }

    private void processTradeLedger(OrderBook.TradeEvent t, int symbolId) {
        // 簡化: 買家得 BTC, 賣家得 USDT
        ledger.addAvailable(t.takerUserId, 1, t.qty); // Taker (Buyer) 得到 BTC
        ledger.addAvailable(t.makerUserId, 2, t.price * t.qty / 100_000_000L); // Maker (Seller) 得到 USDT
        // 此處應有更嚴謹的凍結返還邏輯...
    }

    private void sendExecutionReport(long userId, long orderId, String cid, OrderStatus status, 
                                    long lastPrice, long lastQty, long cumQty, long avgPrice) {
        executionEncoder.wrapAndApplyHeader(outboundBuffer, 0, headerEncoder);
        executionEncoder.timestamp(System.currentTimeMillis())
                        .userId(userId)
                        .orderId(orderId)
                        .status(status)
                        .lastPrice(lastPrice)
                        .lastQty(lastQty)
                        .cumQty(cumQty)
                        .avgPrice(avgPrice)
                        .clientOrderId(cid);

        int len = MessageHeaderEncoder.ENCODED_LENGTH + executionEncoder.encodedLength();
        outbound.getQueue().acquireAppender().writeDocument(wire -> {
            wire.write("msgType").int32(executionEncoder.sbeTemplateId());
            wire.write("payload").bytes(outboundBuffer.byteArray(), 0, len);
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
