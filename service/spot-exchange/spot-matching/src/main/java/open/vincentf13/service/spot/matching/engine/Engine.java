package open.vincentf13.service.spot.matching.engine;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ExcerptTailer;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.sbe.SbeCodec;
import open.vincentf13.service.spot.infra.util.DecimalUtil;
import open.vincentf13.service.spot.model.CidKey;
import open.vincentf13.service.spot.model.Order;
import open.vincentf13.service.spot.model.Progress;
import open.vincentf13.service.spot.model.Trade;
import open.vincentf13.service.spot.sbe.*;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.util.List;

import static open.vincentf13.service.spot.infra.Constants.*;

@Slf4j
@Component
public class Engine extends Worker {
    private final Ledger ledger;
    private final Int2ObjectHashMap<OrderBook> books = new Int2ObjectHashMap<>();
    private final Long2ObjectHashMap<Order> activeOrderIndex = new Long2ObjectHashMap<>();
    
    private final Progress progress = new Progress();
    private final OrderCreateDecoder createDecoder = new OrderCreateDecoder();
    private final OrderCancelDecoder cancelDecoder = new OrderCancelDecoder();
    private final UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(512);
    private final ExecutionReportEncoder executionEncoder = new ExecutionReportEncoder();
    private final Bytes<ByteBuffer> outboundBytes = Bytes.elasticByteBuffer(1024);
    private final UnsafeBuffer outboundSbeBuffer = new UnsafeBuffer(0, 0);
    private ExcerptTailer tailer;
    private boolean isReplaying = false;
    
    public Engine(Ledger ledger) {
        this.ledger = ledger;
    }
    
    @PostConstruct
    public void init() {
        start("matching-engine");
    }
    
    @Override
    protected void onStart() {
        this.tailer = Storage.self().commandQueue().createTailer();
        Progress saved = Storage.self().metadata().get(PK_CORE_PROGRESS);
        if (saved != null) {
            progress.setLastProcessedSeq(saved.getLastProcessedSeq());
            progress.setOrderIdCounter(saved.getOrderIdCounter());
            progress.setTradeIdCounter(saved.getTradeIdCounter());
        } else {
            progress.setOrderIdCounter(1);
            progress.setTradeIdCounter(1);
        }
        
        Storage.self().activeOrders().keySet().forEach(id -> {
            Order o = Storage.self().orders().get(id);
            if (o != null && o.getStatus() < 2) {
                books.computeIfAbsent(o.getSymbolId(), OrderBook::new).add(o);
                activeOrderIndex.put(id, o);
            } else
                Storage.self().activeOrders().remove(id);
        });
        
        if (progress.getLastProcessedSeq() > 0) {
            isReplaying = true;
            tailer.moveToIndex(progress.getLastProcessedSeq());
        }
    }
    
    @Override
    protected int doWork() {
        boolean handled = tailer.readDocument(wire -> {
            long seq = tailer.index();
            int msgType = wire.read("msgType").int32();
            if (isReplaying && seq >= tailer.queue().lastIndex())
                isReplaying = false;
            
            reusableBytes.clear();
            wire.read("payload").bytes(reusableBytes);
            payloadBuffer.wrap(reusableBytes.addressForRead(reusableBytes.readPosition()), (int) reusableBytes.readRemaining());
            
            if (msgType == MSG_TYPE_AUTH)
                handleAuth(wire, seq);
            else if (msgType == MSG_TYPE_ORDER_CREATE)
                dispatchOrderCreate(seq);
            else if (msgType == MSG_TYPE_ORDER_CANCEL)
                dispatchOrderCancel(seq);
            
            progress.setLastProcessedSeq(seq);
            Storage.self().metadata().put(PK_CORE_PROGRESS, progress);
        });
        if (!handled && isReplaying)
            isReplaying = false;
        return handled ? 1 : 0;
    }
    
    private void dispatchOrderCreate(long seq) {
        SbeCodec.decode(payloadBuffer, 0, createDecoder);
        String cid = createDecoder.clientOrderId();
        CidKey key = new CidKey(createDecoder.userId(), cid);
        Long resId = Storage.self().cids().get(key);
        if (resId != null) {
            if (!isReplaying)
                resendReport(resId, createDecoder.userId(), cid, createDecoder.timestamp());
            return;
        }
        Storage.self().cids().put(key, handleOrderCreate(createDecoder, seq, cid));
    }
    
    private void dispatchOrderCancel(long seq) {
        SbeCodec.decode(payloadBuffer, 0, cancelDecoder);
        String cid = cancelDecoder.clientOrderId();
        CidKey key = new CidKey(cancelDecoder.userId(), cid);
        if (Storage.self().cids().containsKey(key))
            return;
        handleOrderCancel(cancelDecoder, seq, cid);
        Storage.self().cids().put(key, ID_REJECTED);
    }
    
    private long handleOrderCreate(OrderCreateDecoder sbe,
                                   long seq,
                                   String cid) {
        long ts = sbe.timestamp();
        long cost = (sbe.side() == Side.BUY) ? DecimalUtil.mulCeil(sbe.price(), sbe.qty()) : sbe.qty();
        int aid = (sbe.side() == Side.BUY) ? ASSET_USDT : ASSET_BTC;
        
        if (!ledger.tryFreeze(sbe.userId(), aid, seq, cost)) {
            sendReport(sbe.userId(), 0, cid, OrderStatus.REJECTED, 0, 0, 0, 0, ts);
            return ID_REJECTED;
        }
        
        long oid = progress.getOrderIdCounter();
        progress.setOrderIdCounter(oid + 1);
        Order o = new Order();
        o.setOrderId(oid);
        o.setClientOrderId(cid);
        o.setUserId(sbe.userId());
        o.setSymbolId((int) sbe.symbolId());
        o.setPrice(sbe.price());
        o.setQty(sbe.qty());
        o.setSide((byte) (sbe.side() == Side.BUY ? 0 : 1));
        o.setStatus((byte) 0);
        o.setVersion(1);
        o.setLastSeq(seq);
        
        activeOrderIndex.put(oid, o);
        persistOrder(o);
        List<OrderBook.TradeEvent> trades = books.computeIfAbsent(o.getSymbolId(), OrderBook::new).match(o);
        for (OrderBook.TradeEvent t : trades) {
            long tid = progress.getTradeIdCounter();
            progress.setTradeIdCounter(tid + 1);
            persistTrade(t, tid, sbe.timestamp(), seq);
            processTradeLedger(t, seq, o);
            syncOrder(t.makerOrderId, seq);
            sendReport(t.makerUserId, t.makerOrderId, "", OrderStatus.PARTIALLY_FILLED, t.price, t.qty, 0, 0, sbe.timestamp());
        }
        syncOrder(oid, seq);
        OrderStatus st = (o.getStatus() == 2) ? OrderStatus.FILLED : OrderStatus.NEW;
        sendReport(sbe.userId(), oid, cid, st, 0, 0, o.getFilled(), 0, ts);
        return oid;
    }
    
    private void handleOrderCancel(OrderCancelDecoder sbe,
                                   long seq,
                                   String cid) {
        Order o = activeOrderIndex.get(sbe.orderId());
        if (o == null || o.getUserId() != sbe.userId()) {
            sendReport(sbe.userId(), sbe.orderId(), cid, OrderStatus.REJECTED, 0, 0, 0, 0, sbe.timestamp());
            return;
        }
        books.get(o.getSymbolId()).remove(o);
        long unf = (o.getSide() == 0) ? DecimalUtil.mulCeil(o.getPrice(), o.getQty() - o.getFilled()) : o.getQty() - o.getFilled();
        ledger.unfreeze(o.getUserId(), (o.getSide() == 0) ? ASSET_USDT : ASSET_BTC, seq, unf);
        o.setStatus((byte) 3);
        o.setLastSeq(seq);
        activeOrderIndex.remove(o.getOrderId());
        persistOrder(o);
        sendReport(o.getUserId(), o.getOrderId(), cid, OrderStatus.CANCELED, 0, 0, o.getFilled(), 0, sbe.timestamp());
    }
    
    private void syncOrder(long id,
                           long seq) {
        Order o = activeOrderIndex.get(id);
        if (o != null) {
            if (o.getFilled() == o.getQty()) {
                o.setStatus((byte) 2);
                activeOrderIndex.remove(id);
            }
            o.setVersion(o.getVersion() + 1);
            o.setLastSeq(seq);
            persistOrder(o);
        }
    }
    
    private void persistOrder(Order o) {
        Order ex = Storage.self().orders().get(o.getOrderId());
        if (ex == null || ex.getLastSeq() < o.getLastSeq()) {
            Storage.self().orders().put(o.getOrderId(), o);
            if (o.getStatus() < 2)
                Storage.self().activeOrders().put(o.getOrderId(), true);
            else
                Storage.self().activeOrders().remove(o.getOrderId());
        }
    }
    
    private void processTradeLedger(OrderBook.TradeEvent t,
                                    long seq,
                                    Order taker) {
        long floor = DecimalUtil.mulFloor(t.price, t.qty);
        long ceil = DecimalUtil.mulCeil(t.price, t.qty);
        if (taker.getSide() == 0) {
            ledger.tradeSettleWithRefund(t.takerUserId, ASSET_USDT, ceil, DecimalUtil.mulCeil(taker.getPrice(), t.qty), ASSET_BTC, t.qty, seq);
            ledger.tradeSettle(t.makerUserId, ASSET_BTC, t.qty, ASSET_USDT, floor, seq);
        } else {
            ledger.tradeSettle(t.takerUserId, ASSET_BTC, t.qty, ASSET_USDT, floor, seq);
            Order m = activeOrderIndex.get(t.makerOrderId);
            long mCeil = (m != null) ? DecimalUtil.mulCeil(m.getPrice(), t.qty) : ceil;
            ledger.tradeSettleWithRefund(t.makerUserId, ASSET_USDT, ceil, mCeil, ASSET_BTC, t.qty, seq);
        }
        if (ceil > floor)
            ledger.addAvailable(SYSTEM_USER_ID, ASSET_USDT, seq, ceil - floor);
    }
    
    private void sendReport(long uid,
                            long oid,
                            String cid,
                            OrderStatus s,
                            long lp,
                            long lq,
                            long cq,
                            long ap,
                            long ts) {
        if (isReplaying)
            return;
        outboundBytes.clear();
        outboundSbeBuffer.wrap(outboundBytes.addressForWrite(0), (int) outboundBytes.realCapacity());
        int len = SbeCodec.encode(outboundSbeBuffer, 0, executionEncoder.timestamp(ts).userId(uid).orderId(oid).status(s).lastPrice(lp).lastQty(lq).cumQty(cq).avgPrice(ap).clientOrderId(cid));
        outboundBytes.writePosition(len);
        Storage.self().resultQueue().acquireAppender().writeDocument(wire -> {
            wire.write("msgType").int32(executionEncoder.sbeTemplateId());
            wire.write("payload").bytes(outboundBytes);
        });
    }
    
    private void resendReport(long oid,
                              long uid,
                              String cid,
                              long ts) {
        if (oid == ID_REJECTED)
            sendReport(uid, 0, cid, OrderStatus.REJECTED, 0, 0, 0, 0, ts);
        else {
            Order o = Storage.self().orders().get(oid);
            if (o != null) {
                OrderStatus s = (o.getStatus() == 2) ? OrderStatus.FILLED : (o.getStatus() == 3) ? OrderStatus.CANCELED : (o.getStatus() == 1) ? OrderStatus.PARTIALLY_FILLED : OrderStatus.NEW;
                sendReport(o.getUserId(), o.getOrderId(), o.getClientOrderId(), s, 0, 0, o.getFilled(), 0, ts);
            }
        }
    }
    
    private void persistTrade(OrderBook.TradeEvent t,
                              long tid,
                              long ts,
                              long seq) {
        Trade r = Storage.self().trades().get(tid);
        if (r == null || r.getLastSeq() < seq) {
            r = new Trade();
            r.setTradeId(tid);
            r.setOrderId(t.makerOrderId);
            r.setPrice(t.price);
            r.setQty(t.qty);
            r.setTime(ts);
            r.setLastSeq(seq);
            Storage.self().trades().put(tid, r);
        }
    }
    
    private void handleAuth(net.openhft.chronicle.wire.WireIn wire,
                            long seq) {
        long userId = wire.read("userId").int64();
        ledger.initBalance(userId, ASSET_BTC, seq);
        ledger.initBalance(userId, ASSET_USDT, seq);
        if (isReplaying)
            return;
        Storage.self().resultQueue().acquireAppender().writeDocument(w -> {
            w.write("topic").text("auth.success");
            w.write("userId").int64(userId);
        });
    }
    
    @Override
    protected void onStop() {
        reusableBytes.releaseLast();
        outboundBytes.releaseLast();
    }
}
