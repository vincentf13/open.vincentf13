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
    // 預分配 Bytes 對象，避免 doWork 中 new
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
        
        // 1. 恢復 ID 計數器
        Long savedId = stateStore.getSystemStateMap().get("orderIdCounter");
        if (savedId != null) {
            this.orderIdCounter = savedId;
        } else {
            // 若無記錄，掃描重建
            stateStore.getOrderMap().forEach((id, o) -> { if (id >= orderIdCounter) orderIdCounter = id + 1; });
        }

        // 2. 從持久化 Map 重建 OrderBook
        stateStore.getOrderMap().forEach((orderId, order) -> {
            if (order.getStatus() < 2) {
                books.computeIfAbsent(order.getSymbolId(), OrderBook::new).add(order);
            }
        });

        // 3. 移動隊列指針
        Long lastSeq = stateStore.getSystemStateMap().get("lastProcessedSeq");
        if (lastSeq != null && lastSeq > 0) {
            tailer.moveToIndex(lastSeq);
        }
    }

    @Override
    protected int doWork() {
        return tailer.readDocument(wire -> {
            long currentIndex = tailer.index();
            int msgType = wire.read("msgType").int32();
            
            switch (msgType) {
                case 103: handleAuth(wire); break;
                case 100: 
                    // --- Zero-GC 讀取：直接讀入預分配的 bytes ---
                    reusableBytes.clear();
                    wire.read("payload").bytes(reusableBytes);
                    payloadBuffer.putBytes(0, reusableBytes.toByteArray()); // 註：toByteArray 仍有拷貝，生產環境應直接用地址映射
                    
                    SbeCodec.decode(payloadBuffer, 0, orderCreateDecoder);
                    processCommand(orderCreateDecoder);
                    break;
            }
            stateStore.getSystemStateMap().put("lastProcessedSeq", currentIndex);
            stateStore.getSystemStateMap().put("orderIdCounter", orderIdCounter);
        }) ? 1 : 0;
    }

    private void processCommand(OrderCreateDecoder sbe) {
        CidKey key = new CidKey(sbe.userId(), sbe.clientOrderId());
        if (stateStore.getCidMap().containsKey(key)) {
            log.warn("重複指令，跳過處理: cid={}", key.getCid());
            return;
        }
        handleOrderCreate(sbe);
        stateStore.getCidMap().put(key, 0L);
    }

    private void handleOrderCreate(OrderCreateDecoder sbe) {
        long timestamp = sbe.timestamp();
        long cost = (sbe.side() == Side.BUY) ? DecimalUtil.multiply(sbe.price(), sbe.qty()) : sbe.qty();
        int assetId = (sbe.side() == Side.BUY) ? 2 : 1;

        if (!ledger.tryFreeze(sbe.userId(), assetId, cost)) {
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

        stateStore.getOrderMap().put(order.getOrderId(), order);

        OrderBook book = books.computeIfAbsent((int)sbe.symbolId(), OrderBook::new);
        List<OrderBook.TradeEvent> trades = book.match(order);

        for (OrderBook.TradeEvent t : trades) {
            processTradeLedger(t);
            updatePersistentOrder(t.makerOrderId);
            sendExecutionReport(t.makerUserId, t.makerOrderId, "", OrderStatus.PARTIALLY_FILLED, t.price, t.qty, 0, 0, timestamp);
        }

        updatePersistentOrder(order.getOrderId());
        OrderStatus finalStatus = (order.getFilled() == order.getQty()) ? OrderStatus.FILLED : OrderStatus.NEW;
        sendExecutionReport(sbe.userId(), order.getOrderId(), sbe.clientOrderId(), finalStatus, 0, 0, order.getFilled(), 0, timestamp);
    }

    private void updatePersistentOrder(long orderId) {
        books.values().forEach(book -> {
            book.findOrder(orderId).ifPresent(o -> stateStore.getOrderMap().put(orderId, o));
        });
        ActiveOrder o = stateStore.getOrderMap().get(orderId);
        if (o != null && o.getFilled() == o.getQty()) {
            o.setStatus((byte)2);
            stateStore.getOrderMap().put(orderId, o);
        }
    }

    private void processTradeLedger(OrderBook.TradeEvent t) {
        ledger.deductFrozen(t.takerUserId, 2, DecimalUtil.multiply(t.price, t.qty));
        ledger.addAvailable(t.takerUserId, 1, t.qty);
        ledger.deductFrozen(t.makerUserId, 1, t.qty);
        ledger.addAvailable(t.makerUserId, 2, DecimalUtil.multiply(t.price, t.qty));
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

    private void handleAuth(net.openhft.chronicle.wire.WireIn wire) {
        long userId = wire.read("userId").int64();
        ledger.getOrCreateBalance(userId, 1);
        ledger.getOrCreateBalance(userId, 2);
        stateStore.getOutboundQueue().acquireAppender().writeDocument(w -> {
            w.write("topic").text("auth.success");
            w.write("userId").int64(userId);
        });
    }

    @Override protected void onStop() {
        if (reusableBytes != null) reusableBytes.release();
    }
}
