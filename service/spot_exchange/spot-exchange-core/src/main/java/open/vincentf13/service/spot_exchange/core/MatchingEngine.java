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
import open.vincentf13.service.spot_exchange.model.TradeRecord;
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
    private long tradeIdCounter = 1;
    private ExcerptTailer tailer;
    private boolean isReplaying = false;

    private final OrderCreateDecoder orderCreateDecoder = new OrderCreateDecoder();
    private final OrderCancelDecoder orderCancelDecoder = new OrderCancelDecoder();
    
    // --- 深度優化：使用預分配的 UnsafeBuffer，不進行數據拷貝 ---
    private final UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(512);
    
    private final ExecutionReportEncoder executionEncoder = new ExecutionReportEncoder();
    private final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    private final UnsafeBuffer outboundBuffer = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));

    public MatchingEngine(StateStore stateStore, LedgerProcessor ledger) {
        this.stateStore = stateStore;
        this.ledger = ledger;
    }

    @PostConstruct
    public void init() { start("matching-engine"); }

    @Override
    protected void onStart() {
        this.tailer = stateStore.getCoreQueue().createTailer();
        this.orderIdCounter = stateStore.getSystemStateMap().getOrDefault("orderIdCounter", 1L);
        this.tradeIdCounter = stateStore.getSystemStateMap().getOrDefault("tradeIdCounter", 1L);

        log.info("正在執行系統啟動校驗與重建...");
        stateStore.getActiveOrderIdMap().keySet().forEach(id -> {
            ActiveOrder o = stateStore.getOrderMap().get(id);
            if (o != null && o.getStatus() < 2) {
                books.computeIfAbsent(o.getSymbolId(), OrderBook::new).add(o);
                activeOrderIndex.put(id, o);
            } else {
                stateStore.getActiveOrderIdMap().remove(id);
            }
        });

        Long lastSeq = stateStore.getSystemStateMap().get("lastProcessedSeq");
        if (lastSeq != null && lastSeq > 0) {
            isReplaying = true;
            tailer.moveToIndex(lastSeq);
        }
    }

    @Override
    protected int doWork() {
        return tailer.readDocument(wire -> {
            long currentIndex = tailer.index();
            int msgType = wire.read("msgType").int32();
            
            if (isReplaying && currentIndex >= tailer.queue().lastIndex()) {
                isReplaying = false;
            }

            // --- 深度優化：真正實現零拷貝 ---
            reusableBytes.clear();
            wire.read("payload").bytes(reusableBytes);
            // 直接 wrap 地址與長度，消除 toByteArray()
            payloadBuffer.wrap(reusableBytes.addressForRead(reusableBytes.readPosition()), 
                             (int)reusableBytes.readRemaining());

            switch (msgType) {
                case 103: handleAuth(wire, currentIndex); break;
                case 100: // OrderCreate
                    SbeCodec.decode(payloadBuffer, 0, orderCreateDecoder);
                    handleOrderCreateWithIdempotency(orderCreateDecoder, currentIndex);
                    break;
                case 101: // OrderCancel
                    SbeCodec.decode(payloadBuffer, 0, orderCancelDecoder);
                    handleOrderCancelWithIdempotency(orderCancelDecoder, currentIndex);
                    break;
            }
            
            stateStore.getSystemStateMap().put("lastProcessedSeq", currentIndex);
            stateStore.getSystemStateMap().put("orderIdCounter", orderIdCounter);
            stateStore.getSystemStateMap().put("tradeIdCounter", tradeIdCounter);
        }) ? 1 : 0;
    }

    private void handleOrderCreateWithIdempotency(OrderCreateDecoder sbe, long seq) {
        CidKey key = new CidKey(sbe.userId(), sbe.clientOrderId());
        Long existingId = stateStore.getCidMap().get(key);
        if (existingId != null) {
            if (!isReplaying) resendReport(existingId, sbe.timestamp());
            return;
        }
        handleOrderCreate(sbe, seq);
        stateStore.getCidMap().put(key, orderIdCounter - 1);
    }

    private void handleOrderCancelWithIdempotency(OrderCancelDecoder sbe, long seq) {
        CidKey key = new CidKey(sbe.userId(), sbe.clientOrderId());
        if (stateStore.getCidMap().containsKey(key)) return;
        handleOrderCancel(sbe, seq);
        stateStore.getCidMap().put(key, 0L);
    }

    private void handleOrderCreate(OrderCreateDecoder sbe, long currentSeq) {
        long ts = sbe.timestamp();
        long cost = (sbe.side() == Side.BUY) ? DecimalUtil.mulCeil(sbe.price(), sbe.qty()) : sbe.qty();
        int assetId = (sbe.side() == Side.BUY) ? 2 : 1;

        if (!ledger.tryFreeze(sbe.userId(), assetId, currentSeq, cost)) {
            sendExecutionReport(sbe.userId(), 0, sbe.clientOrderId(), OrderStatus.REJECTED, 0, 0, 0, 0, ts);
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
        order.setVersion(1);
        order.setLastSeq(currentSeq);

        activeOrderIndex.put(order.getOrderId(), order);
        saveOrderAndIndex(order);

        OrderBook book = books.computeIfAbsent((int)sbe.symbolId(), OrderBook::new);
        List<OrderBook.TradeEvent> trades = book.match(order);

        for (OrderBook.TradeEvent t : trades) {
            persistTrade(t, tradeIdCounter++, ts);
            processTradeLedger(t, currentSeq, order);
            syncOrderState(t.makerOrderId, currentSeq);
            sendExecutionReport(t.makerUserId, t.makerOrderId, "", OrderStatus.PARTIALLY_FILLED, t.price, t.qty, 0, 0, ts);
        }

        syncOrderState(order.getOrderId(), currentSeq);
        OrderStatus finalStatus = (order.getStatus() == 2) ? OrderStatus.FILLED : OrderStatus.NEW;
        sendExecutionReport(sbe.userId(), order.getOrderId(), sbe.clientOrderId(), finalStatus, 0, 0, order.getFilled(), 0, ts);
    }

    private void handleOrderCancel(OrderCancelDecoder sbe, long seq) {
        ActiveOrder order = activeOrderIndex.get(sbe.orderId());
        if (order == null || order.getUserId() != sbe.userId()) {
            sendExecutionReport(sbe.userId(), sbe.orderId(), sbe.clientOrderId(), OrderStatus.REJECTED, 0, 0, 0, 0, sbe.timestamp());
            return;
        }

        books.get(order.getSymbolId()).remove(order);
        long remainingQty = order.getQty() - order.getFilled();
        long unfreezeAmount = (order.getSide() == 0) ? DecimalUtil.mulCeil(order.getPrice(), remainingQty) : remainingQty;
        ledger.unfreeze(order.getUserId(), (order.getSide() == 0) ? 2 : 1, seq, unfreezeAmount);

        order.setStatus((byte)3);
        order.setLastSeq(seq);
        activeOrderIndex.remove(order.getOrderId());
        saveOrderAndIndex(order);

        sendExecutionReport(order.getUserId(), order.getOrderId(), sbe.clientOrderId(), OrderStatus.CANCELED, 0, 0, order.getFilled(), 0, sbe.timestamp());
    }

    private void saveOrderAndIndex(ActiveOrder order) {
        ActiveOrder existing = stateStore.getOrderMap().get(order.getOrderId());
        if (existing == null || existing.getLastSeq() < order.getLastSeq()) {
            stateStore.getOrderMap().put(order.getOrderId(), order);
            if (order.getStatus() < 2) stateStore.getActiveOrderIdMap().put(order.getOrderId(), true);
            else stateStore.getActiveOrderIdMap().remove(order.getOrderId());
        }
    }

    private void resendReport(long orderId, long ts) {
        ActiveOrder o = stateStore.getOrderMap().get(orderId);
        if (o != null) {
            OrderStatus s = (o.getStatus() == 2) ? OrderStatus.FILLED : (o.getStatus() == 3) ? OrderStatus.CANCELED : 
                            (o.getStatus() == 1) ? OrderStatus.PARTIALLY_FILLED : OrderStatus.NEW;
            sendExecutionReport(o.getUserId(), o.getOrderId(), o.getClientOrderId(), s, 0, 0, o.getFilled(), 0, ts);
        }
    }

    private void persistTrade(OrderBook.TradeEvent t, long tid, long ts) {
        TradeRecord r = new TradeRecord();
        r.setTradeId(tid); r.setOrderId(t.makerOrderId); r.setPrice(t.price); r.setQty(t.qty); r.setTime(ts);
        stateStore.getTradeHistoryMap().put(tid, r);
    }

    private void syncOrderState(long orderId, long seq) {
        ActiveOrder o = activeOrderIndex.get(orderId);
        if (o != null) {
            if (o.getFilled() == o.getQty()) {
                o.setStatus((byte)2); activeOrderIndex.remove(orderId);
            }
            o.setVersion(o.getVersion() + 1); o.setLastSeq(seq);
            saveOrderAndIndex(o);
        }
    }

    private void processTradeLedger(OrderBook.TradeEvent t, long currentSeq, ActiveOrder taker) {
        long floor = DecimalUtil.mulFloor(t.price, t.qty);
        long ceil = DecimalUtil.mulCeil(t.price, t.qty);
        if (taker.getSide() == 0) {
            ledger.settlement(t.takerUserId, 2, currentSeq, ceil, DecimalUtil.mulCeil(taker.getPrice(), t.qty));
            ledger.addAvailable(t.takerUserId, 1, currentSeq, t.qty);
            ledger.settlement(t.makerUserId, 1, currentSeq, t.qty, t.qty);
            ledger.addAvailable(t.makerUserId, 2, currentSeq, floor);
        } else {
            ledger.settlement(t.takerUserId, 1, currentSeq, t.qty, t.qty);
            ledger.addAvailable(t.takerUserId, 2, currentSeq, floor);
            ActiveOrder maker = activeOrderIndex.get(t.makerOrderId);
            long mCeil = (maker != null) ? DecimalUtil.mulCeil(maker.getPrice(), t.qty) : ceil;
            ledger.settlement(t.makerUserId, 2, currentSeq, ceil, mCeil);
            ledger.addAvailable(t.makerUserId, 1, currentSeq, t.qty);
        }
    }

    private void sendExecutionReport(long userId, long orderId, String cid, OrderStatus status, long lp, long lq, long cq, long ap, long ts) {
        if (isReplaying) return;
        int len = SbeCodec.encode(outboundBuffer, 0, executionEncoder
            .timestamp(ts).userId(userId).orderId(orderId).status(status)
            .lastPrice(lp).lastQty(lq).cumQty(cq).avgPrice(ap).clientOrderId(cid));
        stateStore.getOutboundQueue().acquireAppender().writeDocument(wire -> {
            wire.write("msgType").int32(executionEncoder.sbeTemplateId());
            byte[] data = new byte[len];
            outboundBuffer.getBytes(0, data);
            wire.write("payload").bytes(data);
        });
    }

    private void handleAuth(net.openhft.chronicle.wire.WireIn wire, long currentSeq) {
        long userId = wire.read("userId").int64();
        ledger.initBalance(userId, 1, currentSeq);
        ledger.initBalance(userId, 2, currentSeq);
        if (isReplaying) return;
        stateStore.getOutboundQueue().acquireAppender().writeDocument(w -> {
            w.write("topic").text("auth.success"); w.write("userId").int64(userId);
        });
    }

    @Override protected void onStop() { if (reusableBytes != null) reusableBytes.releaseLast(); }
}
