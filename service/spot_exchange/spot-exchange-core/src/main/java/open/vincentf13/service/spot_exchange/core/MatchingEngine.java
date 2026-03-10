package open.vincentf13.service.spot_exchange.core;

import jakarta.annotation.PostConstruct;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot_exchange.infra.*;
import open.vincentf13.service.spot_exchange.model.*;
import open.vincentf13.service.spot_exchange.sbe.*;

import java.nio.ByteBuffer;
import java.util.*;

@Component
public class MatchingEngine extends BusySpinWorker {
    private static final long SYSTEM_USER_ID = 0L;
    private static final long ID_REJECTED = 0L; // cidMap 中表示被拒絕的特殊標記
    
    private final StateStore stateStore;
    private final LedgerProcessor ledger;
    private final Map<Integer, OrderBook> books = new HashMap<>();
    private final Map<Long, ActiveOrder> activeOrderIndex = new HashMap<>();
    
    private final SystemProgress progress = new SystemProgress();
    private ExcerptTailer tailer;
    private boolean isReplaying = false;

    private final OrderCreateDecoder orderCreateDecoder = new OrderCreateDecoder();
    private final OrderCancelDecoder orderCancelDecoder = new OrderCancelDecoder();
    private final UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(512);
    private final Bytes<ByteBuffer> outboundBytes = Bytes.elasticByteBuffer(1024);
    private final UnsafeBuffer outboundSbeBuffer = new UnsafeBuffer(0, 0);
    private final ExecutionReportEncoder executionEncoder = new ExecutionReportEncoder();

    public MatchingEngine(StateStore stateStore, LedgerProcessor ledger) {
        this.stateStore = stateStore;
        this.ledger = ledger;
    }

    @PostConstruct
    public void init() { start("matching-engine"); }

    @Override
    protected void onStart() {
        this.tailer = stateStore.getCoreQueue().createTailer();
        SystemProgress saved = stateStore.getSystemProgressMap().get((byte) 1);
        if (saved != null) {
            this.progress.setLastProcessedSeq(saved.getLastProcessedSeq());
            this.progress.setOrderIdCounter(saved.getOrderIdCounter());
            this.progress.setTradeIdCounter(saved.getTradeIdCounter());
        } else {
            this.progress.setOrderIdCounter(1);
            this.progress.setTradeIdCounter(1);
        }

        log.info("正在執行金融級有序重建...");
        stateStore.getActiveOrderIdMap().keySet().forEach(id -> {
            ActiveOrder o = stateStore.getOrderMap().get(id);
            if (o != null && o.getStatus() < 2) {
                books.computeIfAbsent(o.getSymbolId(), OrderBook::new).add(o);
                activeOrderIndex.put(id, o);
            } else stateStore.getActiveOrderIdMap().remove(id);
        });

        if (progress.getLastProcessedSeq() > 0) {
            isReplaying = true;
            tailer.moveToIndex(progress.getLastProcessedSeq());
        }
    }

    @Override
    protected int doWork() {
        boolean handled = tailer.readDocument(wire -> {
            long currentIndex = tailer.index();
            int msgType = wire.read("msgType").int32();

            reusableBytes.clear();
            wire.read("payload").bytes(reusableBytes);
            payloadBuffer.wrap(reusableBytes.addressForRead(reusableBytes.readPosition()), (int)reusableBytes.readRemaining());

            switch (msgType) {
                case 103: handleAuth(wire, currentIndex); break;
                case 100: 
                    SbeCodec.decode(payloadBuffer, 0, orderCreateDecoder);
                    handleOrderCreateWithIdempotency(orderCreateDecoder, currentIndex);
                    break;
                case 101: 
                    SbeCodec.decode(payloadBuffer, 0, orderCancelDecoder);
                    handleOrderCancelWithIdempotency(orderCancelDecoder, currentIndex);
                    break;
            }
            progress.setLastProcessedSeq(currentIndex);
            stateStore.getSystemProgressMap().put((byte) 1, progress);
        });

        if (!handled && isReplaying) {
            log.info("已追上實時進度，重播模式結束");
            isReplaying = false;
        }

        return handled ? 1 : 0;
    }

    private void handleOrderCreateWithIdempotency(OrderCreateDecoder sbe, long seq) {
        String cid = sbe.clientOrderId();
        CidKey key = new CidKey(sbe.userId(), cid);
        Long existingId = stateStore.getCidMap().get(key);
        if (existingId != null) {
            if (!isReplaying && existingId > 0) resendReport(existingId, sbe.userId(), cid, sbe.timestamp());
            else if (!isReplaying && existingId == ID_REJECTED) sendExecutionReport(sbe.userId(), 0, cid, OrderStatus.REJECTED, 0, 0, 0, 0, sbe.timestamp());
            return;
        }
        long assignedId = handleOrderCreate(sbe, seq, cid);
        stateStore.getCidMap().put(key, assignedId);
    }

    private void handleOrderCancelWithIdempotency(OrderCancelDecoder sbe, long seq) {
        String cid = sbe.clientOrderId();
        CidKey key = new CidKey(sbe.userId(), cid);
        if (stateStore.getCidMap().containsKey(key)) return;
        handleOrderCancel(sbe, seq, cid);
        stateStore.getCidMap().put(key, ID_REJECTED);
    }

    private long handleOrderCreate(OrderCreateDecoder sbe, long currentSeq, String cid) {
        long ts = sbe.timestamp();
        long cost = (sbe.side() == Side.BUY) ? DecimalUtil.mulCeil(sbe.price(), sbe.qty()) : sbe.qty();
        int assetId = (sbe.side() == Side.BUY) ? 2 : 1;

        if (!ledger.tryFreeze(sbe.userId(), assetId, currentSeq, cost)) {
            sendExecutionReport(sbe.userId(), 0, cid, OrderStatus.REJECTED, 0, 0, 0, 0, ts);
            return ID_REJECTED;
        }

        long currentOrderId = progress.getOrderIdCounter();
        progress.setOrderIdCounter(currentOrderId + 1);

        ActiveOrder order = new ActiveOrder();
        order.setOrderId(currentOrderId);
        order.setClientOrderId(cid);
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
            long tid = progress.getTradeIdCounter();
            progress.setTradeIdCounter(tid + 1);
            persistTrade(t, tid, ts, currentSeq);
            processTradeLedger(t, currentSeq, order);
            syncOrderState(t.makerOrderId, currentSeq);
            sendExecutionReport(t.makerUserId, t.makerOrderId, "", OrderStatus.PARTIALLY_FILLED, t.price, t.qty, 0, 0, ts);
        }

        syncOrderState(order.getOrderId(), currentSeq);
        OrderStatus finalStatus = (order.getStatus() == 2) ? OrderStatus.FILLED : OrderStatus.NEW;
        sendExecutionReport(sbe.userId(), order.getOrderId(), cid, finalStatus, 0, 0, order.getFilled(), 0, ts);

        return currentOrderId;
    }

    private void handleOrderCancel(OrderCancelDecoder sbe, long seq, String cid) {
        ActiveOrder order = activeOrderIndex.get(sbe.orderId());
        if (order == null || order.getUserId() != sbe.userId()) {
            sendExecutionReport(sbe.userId(), sbe.orderId(), cid, OrderStatus.REJECTED, 0, 0, 0, 0, sbe.timestamp());
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

        sendExecutionReport(order.getUserId(), order.getOrderId(), cid, OrderStatus.CANCELED, 0, 0, order.getFilled(), 0, sbe.timestamp());
    }

    private void saveOrderAndIndex(ActiveOrder order) {
        ActiveOrder existing = stateStore.getOrderMap().get(order.getOrderId());
        if (existing == null || existing.getLastSeq() < order.getLastSeq()) {
            stateStore.getOrderMap().put(order.getOrderId(), order);
            if (order.getStatus() < 2) stateStore.getActiveOrderIdMap().put(order.getOrderId(), true);
            else stateStore.getActiveOrderIdMap().remove(order.getOrderId());
        }
    }

    private void persistTrade(OrderBook.TradeEvent t, long tid, long ts, long seq) {
        TradeRecord r = stateStore.getTradeHistoryMap().get(tid);
        if (r == null || r.getLastSeq() < seq) {
            r = new TradeRecord();
            r.setTradeId(tid); r.setOrderId(t.makerOrderId); r.setPrice(t.price); r.setQty(t.qty); r.setTime(ts); r.setLastSeq(seq);
            stateStore.getTradeHistoryMap().put(tid, r);
        }
    }

    private void syncOrderState(long orderId, long seq) {
        ActiveOrder o = activeOrderIndex.get(orderId);
        if (o != null) {
            if (o.getFilled() == o.getQty()) { o.setStatus((byte)2); activeOrderIndex.remove(orderId); }
            o.setVersion(o.getVersion() + 1); o.setLastSeq(seq); saveOrderAndIndex(o);
        }
    }

    private void processTradeLedger(OrderBook.TradeEvent t, long currentSeq, ActiveOrder taker) {
        long floor = DecimalUtil.mulFloor(t.price, t.qty);
        long ceil = DecimalUtil.mulCeil(t.price, t.qty);
        long dust = ceil - floor;
        if (taker.getSide() == 0) {
            ledger.tradeSettleWithRefund(t.takerUserId, 2, ceil, DecimalUtil.mulCeil(taker.getPrice(), t.qty), 1, t.qty, currentSeq);
            ledger.tradeSettle(t.makerUserId, 1, t.qty, 2, floor, currentSeq);
        } else {
            ledger.tradeSettle(t.takerUserId, 1, t.qty, 2, floor, currentSeq);
            ActiveOrder maker = activeOrderIndex.get(t.makerOrderId);
            long mCeil = (maker != null) ? DecimalUtil.mulCeil(maker.getPrice(), t.qty) : ceil;
            ledger.tradeSettleWithRefund(t.makerUserId, 2, ceil, mCeil, 1, t.qty, currentSeq);
        }
        if (dust > 0) ledger.addAvailable(SYSTEM_USER_ID, 2, currentSeq, dust);
    }

    private void sendExecutionReport(long userId, long orderId, String cid, OrderStatus status, long lp, long lq, long cq, long ap, long ts) {
        if (isReplaying) return;
        outboundBytes.clear();
        outboundSbeBuffer.wrap(outboundBytes.addressForWrite(0), (int)outboundBytes.realCapacity());
        int len = SbeCodec.encode(outboundSbeBuffer, 0, executionEncoder.timestamp(ts).userId(userId).orderId(orderId).status(status).lastPrice(lp).lastQty(lq).cumQty(cq).avgPrice(ap).clientOrderId(cid));
        outboundBytes.writePosition(len);
        stateStore.getOutboundQueue().acquireAppender().writeDocument(wire -> {
            wire.write("msgType").int32(executionEncoder.sbeTemplateId());
            wire.write("payload").bytes(outboundBytes);
        });
    }

    private void handleAuth(net.openhft.chronicle.wire.WireIn wire, long currentSeq) {
        long userId = wire.read("userId").int64();
        ledger.initBalance(userId, 1, currentSeq); ledger.initBalance(userId, 2, currentSeq);
        if (isReplaying) return;
        stateStore.getOutboundQueue().acquireAppender().writeDocument(w -> {
            w.write("topic").text("auth.success"); w.write("userId").int64(userId);
        });
    }

    @Override protected void onStop() { 
        if (reusableBytes != null) reusableBytes.releaseLast(); 
        if (outboundBytes != null) outboundBytes.releaseLast();
    }
}
