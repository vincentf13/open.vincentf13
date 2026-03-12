package open.vincentf13.service.spot.matching.engine;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.util.DecimalUtil;
import open.vincentf13.service.spot.infra.sbe.SbeCodec;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.*;
import open.vincentf13.service.spot.sbe.*;

import java.nio.ByteBuffer;
import java.util.*;

import static open.vincentf13.service.spot.infra.Constants.*;

/** 
 撮合引擎 (Matching Core Engine)
 職責：作為系統的核心狀態機，負責處理所有交易指令、維護內存訂單簿、驅動帳務更新與產生回報
 */
@Slf4j
@Component
public class Engine extends Worker {
    private final Ledger ledger;
    private final Int2ObjectHashMap<OrderBook> books = new Int2ObjectHashMap<>();
    private final Long2ObjectHashMap<Order> activeOrderIndex = new Long2ObjectHashMap<>();
    
    private final Progress progress = new Progress();
    private ExcerptTailer tailer;
    private boolean isReplaying = false;

    private final OrderCreateDecoder createDecoder = new OrderCreateDecoder();
    private final UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(512);
    
    private final ExecutionReportEncoder executionEncoder = new ExecutionReportEncoder();
    private final Bytes<ByteBuffer> outboundBytes = Bytes.elasticByteBuffer(1024);
    private final UnsafeBuffer outboundSbeBuffer = new UnsafeBuffer(0, 0);

    public Engine(Ledger ledger) {
        this.ledger = ledger;
    }

    @PostConstruct public void init() { start("core-matching-engine"); }

    @Override
    protected void onStart() {
        this.tailer = Storage.self().commandQueue().createTailer();
        Progress saved = Storage.self().metadata().get(MetaDataKey.MACHING_ENGINE_POINT);
        if (saved != null) {
            progress.setLastProcessedSeq(saved.getLastProcessedSeq());
            progress.setOrderIdCounter(saved.getOrderIdCounter());
            progress.setTradeIdCounter(saved.getTradeIdCounter());
        } else {
            progress.setOrderIdCounter(1); progress.setTradeIdCounter(1);
        }

        log.info("正在恢復內存訂單簿狀態...");
        Storage.self().activeOrders().keySet().forEach(id -> {
            Order o = Storage.self().orders().get(id);
            if (o != null && o.getStatus() < 2) {
                books.computeIfAbsent(o.getSymbolId(), OrderBook::new).add(o);
                activeOrderIndex.put(id, o);
            } else Storage.self().activeOrders().remove(id);
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
            int msgType = wire.read(ChronicleWireKey.msgType).int32();
            long gwSeq = wire.read(ChronicleWireKey.gwSeq).int64();
            
            if (isReplaying && seq >= tailer.queue().lastIndex()) isReplaying = false;

            reusableBytes.clear(); 
            wire.read(ChronicleWireKey.payload).bytes(reusableBytes);
            payloadBuffer.wrap(reusableBytes.addressForRead(reusableBytes.readPosition()), (int)reusableBytes.readRemaining());

            if (msgType == MsgType.AUTH) handleAuth(wire, gwSeq);
            else if (msgType == MsgType.ORDER_CREATE) dispatchOrderCreate(gwSeq);

            progress.setLastProcessedSeq(seq);
            Storage.self().metadata().put(MetaDataKey.MACHING_ENGINE_POINT, progress);
        });
        if (!handled && isReplaying) isReplaying = false;
        return handled ? 1 : 0;
    }

    private void dispatchOrderCreate(long gwSeq) {
        SbeCodec.decode(payloadBuffer, 0, createDecoder);
        String cid = createDecoder.clientOrderId();
        CidKey key = new CidKey(createDecoder.userId(), cid);
        Long resId = Storage.self().cids().get(key);
        if (resId != null) {
            if (!isReplaying) resendReport(resId, createDecoder.userId(), cid, createDecoder.timestamp(), gwSeq);
            return;
        }
        Storage.self().cids().put(key, handleOrderCreate(createDecoder, gwSeq, cid));
    }

    private long handleOrderCreate(OrderCreateDecoder sbe, long gwSeq, String cid) {
        long ts = sbe.timestamp();
        long cost = (sbe.side() == Side.BUY) ? DecimalUtil.mulCeil(sbe.price(), sbe.qty()) : sbe.qty();
        int aid = (sbe.side() == Side.BUY) ? Asset.USDT : Asset.BTC;

        if (!ledger.tryFreeze(sbe.userId(), aid, gwSeq, cost)) {
            sendReport(sbe.userId(), 0, cid, OrderStatus.REJECTED, 0, 0, 0, 0, ts, gwSeq);
            return ID_REJECTED;
        }

        long oid = progress.getOrderIdCounter(); progress.setOrderIdCounter(oid + 1);
        Order o = new Order();
        o.setOrderId(oid); o.setClientOrderId(cid); o.setUserId(sbe.userId());
        o.setSymbolId((int)sbe.symbolId()); o.setPrice(sbe.price()); o.setQty(sbe.qty());
        o.setSide((byte)(sbe.side() == Side.BUY ? 0 : 1)); o.setStatus((byte)0);
        o.setVersion(1); o.setLastSeq(gwSeq);

        activeOrderIndex.put(oid, o); persistOrder(o);
        List<OrderBook.TradeEvent> trades = books.computeIfAbsent(o.getSymbolId(), OrderBook::new).match(o);
        for (OrderBook.TradeEvent t : trades) {
            long tid = progress.getTradeIdCounter(); progress.setTradeIdCounter(tid + 1);
            persistTrade(t, tid, sbe.timestamp(), gwSeq);
            processTradeLedger(t, gwSeq, o);
            syncOrder(t.makerOrderId, gwSeq);
            sendReport(t.makerUserId, t.makerOrderId, "", OrderStatus.PARTIALLY_FILLED, t.price, t.qty, 0, 0, sbe.timestamp(), gwSeq);
        }
        syncOrder(oid, gwSeq);
        OrderStatus st = (o.getStatus() == 2) ? OrderStatus.FILLED : OrderStatus.NEW;
        sendReport(sbe.userId(), oid, cid, st, 0, 0, o.getFilled(), 0, ts, gwSeq);
        return oid;
    }

    private void syncOrder(long id, long gwSeq) {
        Order o = activeOrderIndex.get(id);
        if (o != null) {
            if (o.getFilled() == o.getQty()) { o.setStatus((byte)2); activeOrderIndex.remove(id); }
            o.setVersion(o.getVersion() + 1); o.setLastSeq(gwSeq); persistOrder(o);
        }
    }

    private void persistOrder(Order o) {
        Order ex = Storage.self().orders().get(o.getOrderId());
        if (ex == null || ex.getLastSeq() < o.getLastSeq()) {
            Storage.self().orders().put(o.getOrderId(), o);
            if (o.getStatus() < 2) Storage.self().activeOrders().put(o.getOrderId(), true);
            else Storage.self().activeOrders().remove(o.getOrderId());
        }
    }

    private void processTradeLedger(OrderBook.TradeEvent t, long gwSeq, Order taker) {
        long floor = DecimalUtil.mulFloor(t.price, t.qty);
        long ceil = DecimalUtil.mulCeil(t.price, t.qty);
        if (taker.getSide() == 0) {
            ledger.tradeSettleWithRefund(t.takerUserId, Asset.USDT, ceil, DecimalUtil.mulCeil(taker.getPrice(), t.qty), Asset.BTC, t.qty, gwSeq);
            ledger.tradeSettle(t.makerUserId, Asset.BTC, t.qty, Asset.USDT, floor, gwSeq);
        } else {
            ledger.tradeSettle(t.takerUserId, Asset.BTC, t.qty, Asset.USDT, floor, gwSeq);
            Order m = activeOrderIndex.get(t.makerOrderId);
            long mCeil = (m != null) ? DecimalUtil.mulCeil(m.getPrice(), t.qty) : ceil;
            ledger.tradeSettleWithRefund(t.makerUserId, Asset.USDT, ceil, mCeil, Asset.BTC, t.qty, gwSeq);
        }
        if (ceil > floor) ledger.addAvailable(PLATFORM_USER_ID, Asset.USDT, gwSeq, ceil - floor);
    }

    private void sendReport(long uid, long oid, String cid, OrderStatus s, long lp, long lq, long cq, long ap, long ts, long gwSeq) {
        if (isReplaying) return;
        outboundBytes.clear();
        outboundSbeBuffer.wrap(outboundBytes.addressForWrite(0), (int)outboundBytes.realCapacity());
        int sbeLen = SbeCodec.encode(outboundSbeBuffer, 0, executionEncoder.timestamp(ts).userId(uid).orderId(oid).status(s).lastPrice(lp).lastQty(lq).cumQty(cq).avgPrice(ap).clientOrderId(cid));
        outboundBytes.writePosition(sbeLen);
        
        Storage.self().resultQueue().acquireAppender().writeDocument(wire -> {
            wire.write(ChronicleWireKey.msgType).int32(executionEncoder.sbeTemplateId());
            wire.write(ChronicleWireKey.matchingSeq).int64(Storage.self().resultQueue().lastIndex()); 
            wire.write(ChronicleWireKey.payload).bytes(outboundBytes);
        });
    }

    private void resendReport(long oid, long uid, String cid, long ts, long gwSeq) {
        if (oid == ID_REJECTED) sendReport(uid, 0, cid, OrderStatus.REJECTED, 0, 0, 0, 0, ts, gwSeq);
        else {
            Order o = Storage.self().orders().get(oid);
            if (o != null) {
                OrderStatus s = (o.getStatus() == 2) ? OrderStatus.FILLED : (o.getStatus() == 3) ? OrderStatus.REJECTED : (o.getStatus() == 1) ? OrderStatus.PARTIALLY_FILLED : OrderStatus.NEW;
                sendReport(o.getUserId(), o.getOrderId(), o.getClientOrderId(), s, 0, 0, o.getFilled(), 0, ts, gwSeq);
            }
        }
    }

    private void persistTrade(OrderBook.TradeEvent t, long tid, long ts, long gwSeq) {
        Trade r = Storage.self().trades().get(tid);
        if (r == null || r.getLastSeq() < gwSeq) {
            r = new Trade(); r.setTradeId(tid); r.setOrderId(t.makerOrderId); r.setPrice(t.price); r.setQty(t.qty); r.setTime(ts); r.setLastSeq(gwSeq);
            Storage.self().trades().put(tid, r);
        }
    }

    private void handleAuth(net.openhft.chronicle.wire.WireIn wire, long gwSeq) {
        long userId = wire.read(ChronicleWireKey.userId).int64();
        ledger.initBalance(userId, Asset.BTC, gwSeq); ledger.initBalance(userId, Asset.USDT, gwSeq);
        if (isReplaying) return;
        Storage.self().resultQueue().acquireAppender().writeDocument(w -> {
            w.write(ChronicleWireKey.topic).text("auth.success");
            w.write(ChronicleWireKey.matchingSeq).int64(Storage.self().resultQueue().lastIndex());
            w.write(ChronicleWireKey.userId).int64(userId);
        });
    }

    @Override protected void onStop() { reusableBytes.releaseLast(); outboundBytes.releaseLast(); }
}
