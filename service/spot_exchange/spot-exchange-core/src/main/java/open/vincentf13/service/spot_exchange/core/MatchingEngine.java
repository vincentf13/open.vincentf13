package open.vincentf13.service.spot_exchange.core;

import jakarta.annotation.PostConstruct;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot_exchange.infra.*;
import open.vincentf13.service.spot_exchange.model.*;
import open.vincentf13.service.spot_exchange.sbe.*;

import java.nio.ByteBuffer;
import java.util.*;

import static open.vincentf13.service.spot_exchange.infra.ExchangeConstants.*;

@Component
public class MatchingEngine extends BusySpinWorker {
    private final StateStore stateStore;
    private final LedgerProcessor ledger;
    private final Int2ObjectHashMap<OrderBook> books = new Int2ObjectHashMap<>();
    private final Long2ObjectHashMap<ActiveOrder> activeOrderIndex = new Long2ObjectHashMap<>();
    private final SystemProgress progress = new SystemProgress();
    private ExcerptTailer tailer;
    private boolean isReplaying = false;

    private final OrderCreateDecoder createDecoder = new OrderCreateDecoder();
    private final OrderCancelDecoder cancelDecoder = new OrderCancelDecoder();
    private final UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(512);
    private final Bytes<ByteBuffer> outboundBytes = Bytes.elasticByteBuffer(1024);
    private final UnsafeBuffer outboundSbeBuffer = new UnsafeBuffer(0, 0);
    private final ExecutionReportEncoder executionEncoder = new ExecutionReportEncoder();

    public MatchingEngine(StateStore stateStore, LedgerProcessor ledger) {
        this.stateStore = stateStore; this.ledger = ledger;
    }

    @PostConstruct public void init() { start("matching-engine"); }

    @Override
    protected void onStart() {
        this.tailer = stateStore.getCoreQueue().createTailer();
        SystemProgress saved = stateStore.getSystemMetadataMap().get(PK_CORE_PROGRESS);
        if (saved != null) {
            progress.setLastProcessedSeq(saved.getLastProcessedSeq());
            progress.setOrderIdCounter(saved.getOrderIdCounter());
            progress.setTradeIdCounter(saved.getTradeIdCounter());
        } else {
            progress.setOrderIdCounter(1); progress.setTradeIdCounter(1);
        }

        stateStore.getActiveOrderIdMap().keySet().forEach(id -> {
            ActiveOrder o = stateStore.getOrderMap().get(id);
            if (o != null && o.getStatus() < 2) {
                books.computeIfAbsent(o.getSymbolId(), OrderBook::new).add(o);
                activeOrderIndex.put(id, o);
            } else stateStore.getActiveOrderIdMap().remove(id);
        });

        if (progress.getLastProcessedSeq() > 0) {
            isReplaying = true; tailer.moveToIndex(progress.getLastProcessedSeq());
        }
    }

    @Override
    protected int doWork() {
        boolean handled = tailer.readDocument(wire -> {
            long seq = tailer.index();
            int msgType = wire.read("msgType").int32();
            if (isReplaying && seq >= tailer.queue().lastIndex()) isReplaying = false;

            reusableBytes.clear(); wire.read("payload").bytes(reusableBytes);
            payloadBuffer.wrap(reusableBytes.addressForRead(reusableBytes.readPosition()), (int)reusableBytes.readRemaining());

            if (msgType == 103) handleAuth(wire, seq);
            else if (msgType == createDecoder.sbeTemplateId()) dispatchOrderCreate(seq);
            else if (msgType == cancelDecoder.sbeTemplateId()) dispatchOrderCancel(seq);

            progress.setLastProcessedSeq(seq);
            stateStore.getSystemMetadataMap().put(PK_CORE_PROGRESS, progress);
        });
        if (!handled && isReplaying) isReplaying = false;
        return handled ? 1 : 0;
    }

    private void dispatchOrderCreate(long seq) {
        SbeCodec.decode(payloadBuffer, 0, createDecoder);
        String cid = createDecoder.clientOrderId();
        CidKey key = new CidKey(createDecoder.userId(), cid);
        Long resId = stateStore.getCidMap().get(key);
        if (resId != null) {
            if (!isReplaying) resendReport(resId, createDecoder.userId(), cid, createDecoder.timestamp());
            return;
        }
        stateStore.getCidMap().put(key, handleOrderCreate(createDecoder, seq, cid));
    }

    private void dispatchOrderCancel(long seq) {
        SbeCodec.decode(payloadBuffer, 0, cancelDecoder);
        String cid = cancelDecoder.clientOrderId();
        CidKey key = new CidKey(cancelDecoder.userId(), cid);
        if (stateStore.getCidMap().containsKey(key)) return;
        handleOrderCancel(cancelDecoder, seq, cid);
        stateStore.getCidMap().put(key, ID_REJECTED);
    }

    private long handleOrderCreate(OrderCreateDecoder sbe, long seq, String cid) {
        long cost = (sbe.side() == Side.BUY) ? DecimalUtil.mulCeil(sbe.price(), sbe.qty()) : sbe.qty();
        int aid = (sbe.side() == Side.BUY) ? ASSET_USDT : ASSET_BTC;

        if (!ledger.tryFreeze(sbe.userId(), aid, seq, cost)) {
            sendReport(sbe.userId(), 0, cid, OrderStatus.REJECTED, 0, 0, 0, 0, sbe.timestamp());
            return ID_REJECTED;
        }

        long oid = progress.getOrderIdCounter(); progress.setOrderIdCounter(oid + 1);
        ActiveOrder o = new ActiveOrder();
        o.setOrderId(oid); o.setClientOrderId(cid); o.setUserId(sbe.userId());
        o.setSymbolId((int)sbe.symbolId()); o.setPrice(sbe.price()); o.setQty(sbe.qty());
        o.setSide((byte)(sbe.side() == Side.BUY ? 0 : 1)); o.setStatus((byte)0);
        o.setVersion(1); o.setLastSeq(seq);

        activeOrderIndex.put(oid, o); saveOrder(o);
        List<OrderBook.TradeEvent> trades = books.computeIfAbsent(o.getSymbolId(), OrderBook::new).match(o);
        for (OrderBook.TradeEvent t : trades) {
            long tid = progress.getTradeIdCounter(); progress.setTradeIdCounter(tid + 1);
            persistTrade(t, tid, sbe.timestamp(), seq);
            processTradeLedger(t, seq, o);
            syncOrder(t.makerOrderId, seq);
            sendReport(t.makerUserId, t.makerOrderId, "", OrderStatus.PARTIALLY_FILLED, t.price, t.qty, 0, 0, sbe.timestamp());
        }
        syncOrder(oid, seq);
        OrderStatus st = (o.getStatus() == 2) ? OrderStatus.FILLED : OrderStatus.NEW;
        sendReport(sbe.userId(), oid, cid, st, 0, 0, o.getFilled(), 0, sbe.timestamp());
        return oid;
    }

    private void handleOrderCancel(OrderCancelDecoder sbe, long seq, String cid) {
        ActiveOrder o = activeOrderIndex.get(sbe.orderId());
        if (o == null || o.getUserId() != sbe.userId()) {
            sendReport(sbe.userId(), sbe.orderId(), cid, OrderStatus.REJECTED, 0, 0, 0, 0, sbe.timestamp());
            return;
        }
        books.get(o.getSymbolId()).remove(o);
        long unf = (o.getSide() == 0) ? DecimalUtil.mulCeil(o.getPrice(), o.getQty() - o.getFilled()) : o.getQty() - o.getFilled();
        ledger.unfreeze(o.getUserId(), (o.getSide() == 0) ? ASSET_USDT : ASSET_BTC, seq, unf);
        o.setStatus((byte)3); o.setLastSeq(seq); activeOrderIndex.remove(o.getOrderId());
        saveOrder(o);
        sendReport(o.getUserId(), o.getOrderId(), cid, OrderStatus.CANCELED, 0, 0, o.getFilled(), 0, sbe.timestamp());
    }

    private void syncOrder(long id, long seq) {
        ActiveOrder o = activeOrderIndex.get(id);
        if (o != null) {
            if (o.getFilled() == o.getQty()) { o.setStatus((byte)2); activeOrderIndex.remove(id); }
            o.setVersion(o.getVersion() + 1); o.setLastSeq(seq); saveOrder(o);
        }
    }

    private void saveOrder(ActiveOrder o) {
        ActiveOrder ex = stateStore.getOrderMap().get(o.getOrderId());
        if (ex == null || ex.getLastSeq() < o.getLastSeq()) {
            stateStore.getOrderMap().put(o.getOrderId(), o);
            if (o.getStatus() < 2) stateStore.getActiveOrderIdMap().put(o.getOrderId(), true);
            else stateStore.getActiveOrderIdMap().remove(o.getOrderId());
        }
    }

    private void processTradeLedger(OrderBook.TradeEvent t, long seq, ActiveOrder taker) {
        long floor = DecimalUtil.mulFloor(t.price, t.qty);
        long ceil = DecimalUtil.mulCeil(t.price, t.qty);
        if (taker.getSide() == 0) {
            ledger.tradeSettleWithRefund(t.takerUserId, ASSET_USDT, ceil, DecimalUtil.mulCeil(taker.getPrice(), t.qty), ASSET_BTC, t.qty, seq);
            ledger.tradeSettle(t.makerUserId, ASSET_BTC, t.qty, ASSET_USDT, floor, seq);
        } else {
            ledger.tradeSettle(t.takerUserId, ASSET_BTC, t.qty, ASSET_USDT, floor, seq);
            ActiveOrder m = activeOrderIndex.get(t.makerOrderId);
            long mCeil = (m != null) ? DecimalUtil.mulCeil(m.getPrice(), t.qty) : ceil;
            ledger.tradeSettleWithRefund(t.makerUserId, ASSET_USDT, ceil, mCeil, ASSET_BTC, t.qty, seq);
        }
        if (ceil > floor) ledger.addAvailable(SYSTEM_USER_ID, ASSET_USDT, seq, ceil - floor);
    }

    private void sendReport(long uid, long oid, String cid, OrderStatus s, long lp, long lq, long cq, long ap, long ts) {
        if (isReplaying) return;
        outboundBytes.clear();
        outboundSbeBuffer.wrap(outboundBytes.addressForWrite(0), (int)outboundBytes.realCapacity());
        int len = SbeCodec.encode(outboundSbeBuffer, 0, executionEncoder.timestamp(ts).userId(uid).orderId(oid).status(s).lastPrice(lp).lastQty(lq).cumQty(cq).avgPrice(ap).clientOrderId(cid));
        outboundBytes.writePosition(len);
        stateStore.getOutboundQueue().acquireAppender().writeDocument(wire -> {
            wire.write("msgType").int32(executionEncoder.sbeTemplateId()); wire.write("payload").bytes(outboundBytes);
        });
    }

    private void resendReport(long oid, long uid, String cid, long ts) {
        if (oid == ID_REJECTED) sendReport(uid, 0, cid, OrderStatus.REJECTED, 0, 0, 0, 0, ts);
        else {
            ActiveOrder o = stateStore.getOrderMap().get(oid);
            if (o != null) {
                OrderStatus s = (o.getStatus() == 2) ? OrderStatus.FILLED : (o.getStatus() == 3) ? OrderStatus.CANCELED : (o.getStatus() == 1) ? OrderStatus.PARTIALLY_FILLED : OrderStatus.NEW;
                sendReport(o.getUserId(), o.getOrderId(), o.getClientOrderId(), s, 0, 0, o.getFilled(), 0, ts);
            }
        }
    }

    private void persistTrade(OrderBook.TradeEvent t, long tid, long ts, long seq) {
        TradeRecord r = stateStore.getTradeHistoryMap().get(tid);
        if (r == null || r.getLastSeq() < seq) {
            r = new TradeRecord(); r.setTradeId(tid); r.setOrderId(t.makerOrderId); r.setPrice(t.price); r.setQty(t.qty); r.setTime(ts); r.setLastSeq(seq);
            stateStore.getTradeHistoryMap().put(tid, r);
        }
    }

    private void handleAuth(net.openhft.chronicle.wire.WireIn wire, long seq) {
        long userId = wire.read("userId").int64();
        ledger.initBalance(userId, ASSET_BTC, seq); ledger.initBalance(userId, ASSET_USDT, seq);
        if (isReplaying) return;
        stateStore.getOutboundQueue().acquireAppender().writeDocument(w -> { w.write("topic").text("auth.success"); w.write("userId").int64(userId); });
    }

    @Override protected void onStop() { reusableBytes.releaseLast(); outboundBytes.releaseLast(); }
}
