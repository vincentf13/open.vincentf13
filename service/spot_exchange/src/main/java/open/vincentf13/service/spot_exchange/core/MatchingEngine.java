package open.vincentf13.service.spot_exchange.core;

import io.aeron.Aeron;
import jakarta.annotation.PostConstruct;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot_exchange.infra.BusySpinWorker;
import open.vincentf13.service.spot_exchange.infra.DecimalUtil;
import open.vincentf13.service.spot_exchange.model.ActiveOrder;
import open.vincentf13.service.spot_exchange.sbe.*;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 
  撮合引擎核心邏輯單元 (Logic Unit)
  單線程忙等 (Busy-spin) 處理指令
 */
@Component
public class MatchingEngine extends BusySpinWorker {
    private final OutboundSequencer outbound;
    private final LedgerProcessor ledger;
    
    private final Map<Integer, OrderBook> books = new HashMap<>();
    private long orderIdCounter = 1;
    private ChronicleQueue coreQueue;
    private ExcerptTailer tailer;

    private final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    private final OrderCreateDecoder orderCreateDecoder = new OrderCreateDecoder();
    private final UnsafeBuffer payloadBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(256));
    
    private final ExecutionReportEncoder executionEncoder = new ExecutionReportEncoder();
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final UnsafeBuffer outboundBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(512));

    public MatchingEngine(OutboundSequencer outbound, LedgerProcessor ledger) {
        this.outbound = outbound;
        this.ledger = ledger;
    }

    @PostConstruct
    public void init() {
        start("matching-engine");
    }

    @Override
    protected void onStart() {
        this.coreQueue = SingleChronicleQueueBuilder.binary("data/spot_exchange/core-queue").build();
        this.tailer = coreQueue.createTailer();
    }

    @Override
    protected int doWork() {
        boolean handled = tailer.readDocument(wire -> {
            int msgType = wire.read("msgType").int32();
            switch (msgType) {
                case 103: handleAuth(wire); break;
                case 100: 
                    byte[] bytes = wire.read("payload").bytes();
                    payloadBuffer.putBytes(0, bytes);
                    headerDecoder.wrap(payloadBuffer, 0);
                    orderCreateDecoder.wrap(payloadBuffer, MessageHeaderDecoder.ENCODED_LENGTH, headerDecoder.blockLength(), headerDecoder.version());
                    handleOrderCreate(orderCreateDecoder);
                    break;
                default:
                    log.warn("未知指令類型: {}", msgType);
            }
        });
        return handled ? 1 : 0;
    }

    private void handleOrderCreate(OrderCreateDecoder sbe) {
        long userId = sbe.userId();
        long price = sbe.price();
        long qty = sbe.qty();
        Side side = sbe.side();
        long symbolId = sbe.symbolId();

        // 1. 風控凍結
        long cost = (side == Side.BUY) ? DecimalUtil.multiply(price, qty) : qty;
        int assetToFreeze = (side == Side.BUY) ? 2 : 1;

        if (!ledger.tryFreeze(userId, assetToFreeze, cost)) {
            sendExecutionReport(userId, 0, sbe.clientOrderId(), OrderStatus.REJECTED, 0, 0, 0, 0);
            return;
        }

        // 2. 建立訂單對象
        ActiveOrder order = new ActiveOrder();
        order.setOrderId(orderIdCounter++);
        order.setUserId(userId);
        order.setPrice(price);
        order.setQty(qty);
        order.setSide((byte)(side == Side.BUY ? 0 : 1));

        // 3. 執行撮合
        OrderBook book = books.computeIfAbsent((int)symbolId, OrderBook::new);
        List<OrderBook.TradeEvent> trades = book.match(order);

        // 4. 處理成交帳務
        for (OrderBook.TradeEvent t : trades) {
            processTradeLedger(t);
            sendExecutionReport(t.makerUserId, t.makerOrderId, "", OrderStatus.PARTIALLY_FILLED, t.price, t.qty, 0, 0);
        }

        // 5. 推送 Taker 的最終回報
        OrderStatus finalStatus = (order.getFilled() == order.getQty()) ? OrderStatus.FILLED : OrderStatus.NEW;
        sendExecutionReport(userId, order.getOrderId(), sbe.clientOrderId(), finalStatus, 0, 0, order.getFilled(), 0);
    }

    private void processTradeLedger(OrderBook.TradeEvent t) {
        ledger.addAvailable(t.takerUserId, 1, t.qty);
        ledger.addAvailable(t.makerUserId, 2, DecimalUtil.multiply(t.price, t.qty));
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

    private void handleAuth(net.openhft.chronicle.wire.WireIn wire) {
        long userId = wire.read("userId").int64();
        ledger.getOrCreateBalance(userId, 1);
        ledger.getOrCreateBalance(userId, 2);
        
        outbound.getQueue().acquireAppender().writeDocument(w -> {
            w.write("topic").text("auth.success");
            w.write("userId").int64(userId);
        });
    }

    @Override
    protected void onStop() {
        if (coreQueue != null) coreQueue.close();
    }
}
