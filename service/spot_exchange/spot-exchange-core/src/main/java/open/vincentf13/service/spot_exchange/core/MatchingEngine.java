package open.vincentf13.service.spot_exchange.core;

import jakarta.annotation.PostConstruct;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot_exchange.infra.StateStore;
import open.vincentf13.service.spot_exchange.infra.BusySpinWorker;
import open.vincentf13.service.spot_exchange.infra.DecimalUtil;
import open.vincentf13.service.spot_exchange.infra.SbeCodec;
import open.vincentf13.service.spot_exchange.model.ActiveOrder;
import open.vincentf13.service.spot_exchange.sbe.*;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MatchingEngine extends BusySpinWorker {
    private final StateStore stateStore;
    private final LedgerProcessor ledger;
    private final Map<Integer, OrderBook> books = new HashMap<>();
    private long orderIdCounter = 1;
    private ExcerptTailer tailer;

    private final OrderCreateDecoder orderCreateDecoder = new OrderCreateDecoder();
    private final UnsafeBuffer payloadBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(256));
    private final ExecutionReportEncoder executionEncoder = new ExecutionReportEncoder();
    private final UnsafeBuffer outboundBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(512));

    public MatchingEngine(StateStore stateStore, LedgerProcessor ledger) {
        this.stateStore = stateStore;
        this.ledger = ledger;
    }

    @PostConstruct
    public void init() { start("matching-engine"); }

    @Override
    protected void onStart() { this.tailer = stateStore.getCoreQueue().createTailer(); }

    @Override
    protected int doWork() {
        return tailer.readDocument(wire -> {
            int msgType = wire.read("msgType").int32();
            switch (msgType) {
                case 103: handleAuth(wire); break;
                case 100: 
                    byte[] bytes = wire.read("payload").bytes();
                    payloadBuffer.putBytes(0, bytes);
                    SbeCodec.decode(payloadBuffer, 0, orderCreateDecoder);
                    handleOrderCreate(orderCreateDecoder);
                    break;
            }
        }) ? 1 : 0;
    }

    private void handleOrderCreate(OrderCreateDecoder sbe) {
        long cost = (sbe.side() == Side.BUY) ? DecimalUtil.multiply(sbe.price(), sbe.qty()) : sbe.qty();
        int assetId = (sbe.side() == Side.BUY) ? 2 : 1;

        if (!ledger.tryFreeze(sbe.userId(), assetId, cost)) {
            sendExecutionReport(sbe.userId(), 0, sbe.clientOrderId(), OrderStatus.REJECTED, 0, 0, 0, 0);
            return;
        }

        ActiveOrder order = new ActiveOrder();
        order.setOrderId(orderIdCounter++);
        order.setUserId(sbe.userId());
        order.setPrice(sbe.price());
        order.setQty(sbe.qty());
        order.setSide((byte)(sbe.side() == Side.BUY ? 0 : 1));

        OrderBook book = books.computeIfAbsent((int)sbe.symbolId(), OrderBook::new);
        List<OrderBook.TradeEvent> trades = book.match(order);

        for (OrderBook.TradeEvent t : trades) {
            processTradeLedger(t);
            sendExecutionReport(t.makerUserId, t.makerOrderId, "", OrderStatus.PARTIALLY_FILLED, t.price, t.qty, 0, 0);
        }

        OrderStatus finalStatus = (order.getFilled() == order.getQty()) ? OrderStatus.FILLED : OrderStatus.NEW;
        sendExecutionReport(sbe.userId(), order.getOrderId(), sbe.clientOrderId(), finalStatus, 0, 0, order.getFilled(), 0);
    }

    private void processTradeLedger(OrderBook.TradeEvent t) {
        ledger.addAvailable(t.takerUserId, 1, t.qty);
        ledger.addAvailable(t.makerUserId, 2, DecimalUtil.multiply(t.price, t.qty));
    }

    private void sendExecutionReport(long userId, long orderId, String cid, OrderStatus status, long lp, long lq, long cq, long ap) {
        int len = SbeCodec.encode(outboundBuffer, 0, executionEncoder
            .timestamp(System.currentTimeMillis()).userId(userId).orderId(orderId).status(status)
            .lastPrice(lp).lastQty(lq).cumQty(cq).avgPrice(ap).clientOrderId(cid));

        stateStore.getOutboundQueue().acquireAppender().writeDocument(wire -> {
            wire.write("msgType").int32(executionEncoder.sbeTemplateId());
            wire.write("payload").bytes(outboundBuffer.byteArray(), 0, len);
        });
    }

    private void handleAuth(net.openhft.chronicle.wire.WireIn wire) {
        long userId = wire.read("userId").int64();
        ledger.getOrCreateBalance(userId, 1);
        ledger.getOrCreateBalance(userId, 2);
        stateStore.getOutboundQueue().acquireAppender().writeDocument(w -> {
            w.write("topic").text("auth.success");
            w.write("userId").int64(userId);
        });
    }

    @Override protected void onStop() {}
}
