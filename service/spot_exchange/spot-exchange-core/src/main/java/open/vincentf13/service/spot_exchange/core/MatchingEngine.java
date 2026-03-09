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

    private final OrderCreateDecoder orderCreateDecoder = new OrderCreateDecoder();
    private final UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0); // 延後 wrap
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

        log.info("正在快速重建活躍訂單簿...");
        List<ActiveOrder> activeOrders = new ArrayList<>();
        stateStore.getActiveOrderIdMap().keySet().forEach(id -> {
            ActiveOrder o = stateStore.getOrderMap().get(id);
            if (o != null) activeOrders.add(o);
        });

        activeOrders.stream().sorted(Comparator.comparingLong(ActiveOrder::getOrderId)).forEach(order -> {
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
                    // --- 真正的 Zero-GC 讀取：消除 toByteArray() ---
                    reusableBytes.clear();
                    wire.read("payload").bytes(reusableBytes);
                    // 直接 wrap reusableBytes 的底層地址，實現真正的零物件分配
                    payloadBuffer.wrap(reusableBytes.addressForRead(reusableBytes.readPosition()), (int)reusableBytes.readRemaining());
                    
                    SbeCodec.decode(payloadBuffer, 0, orderCreateDecoder);
                    processCommand(orderCreateDecoder, currentIndex);
                    break;
            }
            stateStore.getSystemStateMap().put("lastProcessedSeq", currentIndex);
            stateStore.getSystemStateMap().put("orderIdCounter", orderIdCounter);
            stateStore.getSystemStateMap().put("tradeIdCounter", tradeIdCounter);
        }) ? 1 : 0;
    }

    private void processCommand(OrderCreateDecoder sbe, long currentSeq) {
        CidKey key = new CidKey(sbe.userId(), sbe.clientOrderId());
        Long existingOrderId = stateStore.getCidMap().get(key);
        if (existingOrderId != null) {
            log.warn("檢測到重複指令，執行回報重發: cid={}", key.getCid());
            ActiveOrder order = stateStore.getOrderMap().get(existingOrderId);
            if (order != null) {
                OrderStatus s = (order.getStatus() == 2) ? OrderStatus.FILLED : (order.getStatus() == 1) ? OrderStatus.PARTIALLY_FILLED : OrderStatus.NEW;
                sendExecutionReport(order.getUserId(), order.getOrderId(), order.getClientOrderId(), s, 0, 0, order.getFilled(), 0, sbe.timestamp());
            }
            return;
        }
        handleOrderCreate(sbe, currentSeq);
        stateStore.getCidMap().put(key, orderIdCounter - 1);
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
        saveOrder(order);
        stateStore.getActiveOrderIdMap().put(order.getOrderId(), true);

        OrderBook book = books.computeIfAbsent((int)sbe.symbolId(), OrderBook::new);
        List<OrderBook.TradeEvent> trades = book.match(order);

        for (OrderBook.TradeEvent t : trades) {
            long tid = tradeIdCounter++;
            persistTrade(t, tid, ts);
            processTradeLedger(t, currentSeq, order);
            syncOrderState(t.makerOrderId, currentSeq);
            sendExecutionReport(t.makerUserId, t.makerOrderId, "", OrderStatus.PARTIALLY_FILLED, t.price, t.qty, 0, 0, ts);
        }

        syncOrderState(order.getOrderId(), currentSeq);
        OrderStatus finalStatus = (order.getStatus() == 2) ? OrderStatus.FILLED : OrderStatus.NEW;
        sendExecutionReport(sbe.userId(), order.getOrderId(), sbe.clientOrderId(), finalStatus, 0, 0, order.getFilled(), 0, ts);
    }

    private void saveOrder(ActiveOrder order) {
        ActiveOrder existing = stateStore.getOrderMap().get(order.getOrderId());
        if (existing == null || existing.getLastSeq() < order.getLastSeq()) {
            stateStore.getOrderMap().put(order.getOrderId(), order);
        }
    }

    private void persistTrade(OrderBook.TradeEvent t, long tid, long ts) {
        TradeRecord r = new TradeRecord();
        r.setTradeId(tid);
        r.setOrderId(t.makerOrderId);
        r.setPrice(t.price);
        r.setQty(t.qty);
        r.setTime(ts);
        stateStore.getTradeHistoryMap().put(tid, r);
    }

    private void syncOrderState(long orderId, long currentSeq) {
        ActiveOrder order = activeOrderIndex.get(orderId);
        if (order != null) {
            if (order.getFilled() == order.getQty()) {
                order.setStatus((byte)2);
                activeOrderIndex.remove(orderId);
                stateStore.getActiveOrderIdMap().remove(orderId);
            }
            order.setVersion(order.getVersion() + 1);
            order.setLastSeq(currentSeq);
            saveOrder(order);
        }
    }

    private void processTradeLedger(OrderBook.TradeEvent t, long currentSeq, ActiveOrder taker) {
        long floor = DecimalUtil.mulFloor(t.price, t.qty);
        long ceil = DecimalUtil.mulCeil(t.price, t.qty);
        
        if (taker.getSide() == 0) { // Taker BUY
            long takerFrozenForThisQty = DecimalUtil.mulCeil(taker.getPrice(), t.qty);
            ledger.settlement(t.takerUserId, 2, currentSeq, ceil, takerFrozenForThisQty);
            ledger.addAvailable(t.takerUserId, 1, currentSeq, t.qty);
            
            ledger.settlement(t.makerUserId, 1, currentSeq, t.qty, t.qty);
            ledger.addAvailable(t.makerUserId, 2, currentSeq, floor);
        } else { // Taker SELL
            ledger.settlement(t.takerUserId, 1, currentSeq, t.qty, t.qty);
            ledger.addAvailable(t.takerUserId, 2, currentSeq, floor);
            
            // --- 修正：使用 activeOrderIndex $O(1)$ 獲取 Maker 掛單價 ---
            ActiveOrder makerOrder = activeOrderIndex.get(t.makerOrderId);
            long mPrice = (makerOrder != null) ? makerOrder.getPrice() : t.price; 
            long makerFrozenForThisQty = DecimalUtil.mulCeil(mPrice, t.qty);
            
            ledger.settlement(t.makerUserId, 2, currentSeq, ceil, makerFrozenForThisQty);
            ledger.addAvailable(t.makerUserId, 1, currentSeq, t.qty);
        }
    }

    private void sendExecutionReport(long userId, long orderId, String cid, OrderStatus status, long lp, long lq, long cq, long ap, long ts) {
        int len = SbeCodec.encode(outboundBuffer, 0, executionEncoder
            .timestamp(ts).userId(userId).orderId(orderId).status(status)
            .lastPrice(lp).lastQty(lq).cumQty(cq).avgPrice(ap).clientOrderId(cid));
        stateStore.getOutboundQueue().acquireAppender().writeDocument(wire -> {
            wire.write("msgType").int32(executionEncoder.sbeTemplateId());
            wire.write("payload").bytes(outboundBuffer, 0, len);
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
