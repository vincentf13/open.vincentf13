package open.vincentf13.service.spot_exchange.core;

import jakarta.annotation.PostConstruct;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot_exchange.infra.BusySpinWorker;
import open.vincentf13.service.spot_exchange.infra.DecimalUtil;
import open.vincentf13.service.spot_exchange.infra.SbeCodec;
import open.vincentf13.service.spot_exchange.infra.StateStore;
import open.vincentf13.service.spot_exchange.model.ActiveOrder;
import open.vincentf13.service.spot_exchange.model.CidKey;
import open.vincentf13.service.spot_exchange.sbe.*;

import java.nio.ByteBuffer;
import java.util.*;

@Component
public class MatchingEngine extends BusySpinWorker {
    private final StateStore stateStore;
    private final LedgerProcessor ledger;
    
    private final Map<Integer, OrderBook> books = new HashMap<>();
    private final Map<Long, ActiveOrder> activeOrderIndex = new HashMap<>();
    
    private long orderIdCounter = 1;
    private ExcerptTailer tailer;

    private final OrderCreateDecoder orderCreateDecoder = new OrderCreateDecoder();
    private final UnsafeBuffer payloadBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(256));
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(256);
    
    private final ExecutionReportEncoder executionEncoder = new ExecutionReportEncoder();
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final UnsafeBuffer outboundBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(512));

    public MatchingEngine(StateStore stateStore, LedgerProcessor ledger) {
        this.stateStore = stateStore;
        this.ledger = ledger;
    }

    @PostConstruct
    public void init() { start("matching-engine"); }

    @Override
    protected void onStart() {
        this.tailer = stateStore.getCoreQueue().createTailer();
        Long savedId = stateStore.getSystemStateMap().get("orderIdCounter");
        if (savedId != null) this.orderIdCounter = savedId;

        log.info("正在重建內存狀態 (有序)...");
        List<ActiveOrder> activeOrders = new ArrayList<>();
        stateStore.getOrderMap().forEach((id, order) -> { if (order.getStatus() < 2) activeOrders.add(order); });

        activeOrders.stream()
            .sorted(Comparator.comparingLong(ActiveOrder::getOrderId))
            .forEach(order -> {
                books.computeIfAbsent(order.getSymbolId(), OrderBook::new).add(order);
                activeOrderIndex.put(order.getOrderId(), order);
            });

        Long lastSeq = stateStore.getSystemStateMap().get("lastProcessedSeq");
        if (lastSeq != null && lastSeq > 0) tailer.moveToIndex(lastSeq);
    }

    @Override
    protected int doWork() {
        return tailer.readDocument(wire -> {
            long currentIndex = tailer.index();
            int msgType = wire.read("msgType").int32();
            
            switch (msgType) {
                case 103: handleAuth(wire, currentIndex); break;
                case 100: 
                    reusableBytes.clear();
                    wire.read("payload").bytes(reusableBytes);
                    payloadBuffer.wrap(reusableBytes.address(), (int)reusableBytes.readLimit());
                    SbeCodec.decode(payloadBuffer, 0, orderCreateDecoder);
                    processCommand(orderCreateDecoder, currentIndex);
                    break;
            }
            stateStore.getSystemStateMap().put("lastProcessedSeq", currentIndex);
            stateStore.getSystemStateMap().put("orderIdCounter", orderIdCounter);
        }) ? 1 : 0;
    }

    private void processCommand(OrderCreateDecoder sbe, long currentSeq) {
        CidKey key = new CidKey(sbe.userId(), sbe.clientOrderId());
        if (stateStore.getCidMap().containsKey(key)) return;
        handleOrderCreate(sbe, currentSeq);
        stateStore.getCidMap().put(key, 0L);
    }

    private void handleOrderCreate(OrderCreateDecoder sbe, long currentSeq) {
        long timestamp = sbe.timestamp();
        long cost = (sbe.side() == Side.BUY) ? DecimalUtil.multiply(sbe.price(), sbe.qty()) : sbe.qty();
        int assetId = (sbe.side() == Side.BUY) ? 2 : 1;

        // 1. 風控凍結 (帶入當前序號)
        if (!ledger.tryFreeze(sbe.userId(), assetId, currentSeq, cost)) {
            sendExecutionReport(sbe.userId(), 0, sbe.clientOrderId(), OrderStatus.REJECTED, 0, 0, 0, 0, timestamp);
            return;
        }

        ActiveOrder order = new ActiveOrder();
        order.setOrderId(orderIdCounter++);
        order.setClientOrderId(sbe.clientOrderId());
        order.setUserId(sbe.userId());
        order.setSymbolId((int)sbe.symbolId());
        order.setPrice(sbe.price());
        order.setQty(sbe.qty());
        order.setSide((byte)(sbe.side() == Side.BUY ? 0 : 1));
        order.setStatus((byte)0);
        order.setLastSeq(currentSeq); // 打上標籤

        activeOrderIndex.put(order.getOrderId(), order);
        stateStore.getOrderMap().put(order.getOrderId(), order);

        OrderBook book = books.computeIfAbsent((int)sbe.symbolId(), OrderBook::new);
        List<OrderBook.TradeEvent> trades = book.match(order);

        for (OrderBook.TradeEvent t : trades) {
            processTradeLedger(t, currentSeq);
            syncOrderState(t.makerOrderId, currentSeq);
            sendExecutionReport(t.makerUserId, t.makerOrderId, "", OrderStatus.PARTIALLY_FILLED, t.price, t.qty, 0, 0, timestamp);
        }

        syncOrderState(order.getOrderId(), currentSeq);
        OrderStatus finalStatus = (order.getStatus() == 2) ? OrderStatus.FILLED : OrderStatus.NEW;
        sendExecutionReport(sbe.userId(), order.getOrderId(), sbe.clientOrderId(), finalStatus, 0, 0, order.getFilled(), 0, timestamp);
    }

    private void syncOrderState(long orderId, long currentSeq) {
        ActiveOrder order = activeOrderIndex.get(orderId);
        if (order != null) {
            if (order.getFilled() == order.getQty()) {
                order.setStatus((byte)2);
                activeOrderIndex.remove(orderId);
            }
            order.setLastSeq(currentSeq); // 更新序號標籤
            stateStore.getOrderMap().put(orderId, order);
        }
    }

    private void processTradeLedger(OrderBook.TradeEvent t, long currentSeq) {
        ledger.deductFrozen(t.takerUserId, 2, currentSeq, DecimalUtil.multiply(t.price, t.qty));
        ledger.addAvailable(t.takerUserId, 1, currentSeq, t.qty);
        ledger.deductFrozen(t.makerUserId, 1, currentSeq, t.qty);
        ledger.addAvailable(t.makerUserId, 2, currentSeq, DecimalUtil.multiply(t.price, t.qty));
    }

    private void sendExecutionReport(long userId, long orderId, String cid, OrderStatus status, long lp, long lq, long cq, long ap, long ts) {
        int len = SbeCodec.encode(outboundBuffer, 0, executionEncoder
            .timestamp(ts).userId(userId).orderId(orderId).status(status)
            .lastPrice(lp).lastQty(lq).cumQty(cq).avgPrice(ap).clientOrderId(cid));

        stateStore.getOutboundQueue().acquireAppender().writeDocument(wire -> {
            wire.write("msgType").int32(executionEncoder.sbeTemplateId());
            wire.write("payload").bytes(outboundBuffer.byteArray(), 0, len);
        });
    }

    private void handleAuth(net.openhft.chronicle.wire.WireIn wire, long currentSeq) {
        long userId = wire.read("userId").int64();
        ledger.initBalance(userId, 1, currentSeq);
        ledger.initBalance(userId, 2, currentSeq);
        stateStore.getOutboundQueue().acquireAppender().writeDocument(w -> {
            w.write("topic").text("auth.success");
            w.write("userId").int64(userId);
        });
    }

    @Override protected void onStop() { if (reusableBytes != null) reusableBytes.release(); }
}
