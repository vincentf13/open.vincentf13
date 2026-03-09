package open.vincentf13.service.spot_exchange.core;

import jakarta.annotation.PostConstruct;
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
        
        // 1. 從持久化 Map 重建 OrderBook (快照恢復)
        log.info("正在從持久化狀態重建訂單簿...");
        stateStore.getOrderMap().forEach((orderId, order) -> {
            if (order.getStatus() < 2) { // 僅加載 NEW 或 PARTIAL
                OrderBook book = books.computeIfAbsent(order.getSymbolId(), OrderBook::new);
                book.add(order);
                if (orderId >= orderIdCounter) orderIdCounter = orderId + 1;
            }
        });

        // 2. 移動隊列指針
        Long lastSeq = stateStore.getSystemStateMap().get("lastProcessedSeq");
        if (lastSeq != null && lastSeq > 0) {
            log.info("核心引擎從上次處理位置恢復: {}", lastSeq);
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
                    byte[] bytes = wire.read("payload").bytes();
                    payloadBuffer.putBytes(0, bytes);
                    SbeCodec.decode(payloadBuffer, 0, orderCreateDecoder);
                    
                    CidKey key = new CidKey(orderCreateDecoder.userId(), orderCreateDecoder.clientOrderId());
                    if (stateStore.getCidMap().containsKey(key)) {
                        log.warn("重複指令，跳過處理: cid={}", key.getCid());
                    } else {
                        handleOrderCreate(orderCreateDecoder);
                        stateStore.getCidMap().put(key, 0L); // 標記已處理
                    }
                    break;
            }
            stateStore.getSystemStateMap().put("lastProcessedSeq", currentIndex);
        }) ? 1 : 0;
    }

    private void handleOrderCreate(OrderCreateDecoder sbe) {
        long cost = (sbe.side() == Side.BUY) ? DecimalUtil.multiply(sbe.price(), sbe.qty()) : sbe.qty();
        int assetId = (sbe.side() == Side.BUY) ? 2 : 1;

        if (!ledger.tryFreeze(sbe.userId(), assetId, cost)) {
            sendExecutionReport(sbe.userId(), 0, sbe.clientOrderId(), OrderStatus.REJECTED, 0, 0, 0, 0, sbe.timestamp());
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
        order.setStatus((byte)0); // NEW

        // 持久化初始狀態
        stateStore.getOrderMap().put(order.getOrderId(), order);

        OrderBook book = books.computeIfAbsent((int)sbe.symbolId(), OrderBook::new);
        List<OrderBook.TradeEvent> trades = book.match(order);

        for (OrderBook.TradeEvent t : trades) {
            processTradeLedger(t);
            // 同步 Maker 狀態到持久化 Map
            updatePersistentOrder(t.makerOrderId);
            sendExecutionReport(t.makerUserId, t.makerOrderId, "", OrderStatus.PARTIALLY_FILLED, t.price, t.qty, 0, 0, sbe.timestamp());
        }

        // 同步 Taker 狀態到持久化 Map
        updatePersistentOrder(order.getOrderId());
        
        OrderStatus finalStatus = (order.getFilled() == order.getQty()) ? OrderStatus.FILLED : OrderStatus.NEW;
        sendExecutionReport(sbe.userId(), order.getOrderId(), sbe.clientOrderId(), finalStatus, 0, 0, order.getFilled(), 0, sbe.timestamp());
    }

    /** 
      將內存 OrderBook 中的最新狀態同步到持久化 Map
     */
    private void updatePersistentOrder(long orderId) {
        // 在實際高頻場景中，這裡應優化為僅當訂單狀態或成交量變更時寫入
        // 目前 MVP 直接從內存檢索並寫入 Map 以保證一致性
        books.values().forEach(book -> {
            book.findOrder(orderId).ifPresent(o -> stateStore.getOrderMap().put(orderId, o));
        });
        
        // 若訂單已完全成交，雖然從 OrderBook 移除了，但 Map 需標記為 FILLED
        ActiveOrder o = stateStore.getOrderMap().get(orderId);
        if (o != null && o.getFilled() == o.getQty()) {
            o.setStatus((byte)2); // FILLED
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

    @Override protected void onStop() {}
}
