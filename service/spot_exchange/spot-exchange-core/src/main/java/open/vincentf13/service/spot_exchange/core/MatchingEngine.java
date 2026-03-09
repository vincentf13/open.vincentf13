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

        log.info("正在快速重建活躍訂單簿...");
        List<ActiveOrder> activeOrders = new ArrayList<>();
        stateStore.getActiveOrderIdMap().keySet().forEach(orderId -> {
            ActiveOrder order = stateStore.getOrderMap().get(orderId);
            if (order != null) activeOrders.add(order);
        });

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
        Long existingOrderId = stateStore.getCidMap().get(key);
        if (existingOrderId != null) {
            ActiveOrder order = stateStore.getOrderMap().get(existingOrderId);
            if (order != null) {
                OrderStatus status = (order.getStatus() == 2) ? OrderStatus.FILLED : 
                                    (order.getStatus() == 1) ? OrderStatus.PARTIALLY_FILLED : OrderStatus.NEW;
                sendExecutionReport(order.getUserId(), order.getOrderId(), order.getClientOrderId(), 
                                    status, 0, 0, order.getFilled(), 0, sbe.timestamp());
            }
            return;
        }
        handleOrderCreate(sbe, currentSeq);
        stateStore.getCidMap().put(key, orderIdCounter - 1);
    }

    private void handleOrderCreate(OrderCreateDecoder sbe, long currentSeq) {
        long timestamp = sbe.timestamp();
        // 凍結採用 Ceil (買家凍結 USDT, 賣家凍結 BTC)
        long cost = (sbe.side() == Side.BUY) ? DecimalUtil.mulCeil(sbe.price(), sbe.qty()) : sbe.qty();
        int assetId = (sbe.side() == Side.BUY) ? 2 : 1;

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
        order.setVersion(1);
        order.setLastSeq(currentSeq);

        activeOrderIndex.put(order.getOrderId(), order);
        stateStore.getOrderMap().put(order.getOrderId(), order);
        stateStore.getActiveOrderIdMap().put(order.getOrderId(), true);

        OrderBook book = books.computeIfAbsent((int)sbe.symbolId(), OrderBook::new);
        List<OrderBook.TradeEvent> trades = book.match(order);

        for (OrderBook.TradeEvent t : trades) {
            processTradeLedger(t, currentSeq, order);
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
                stateStore.getActiveOrderIdMap().remove(orderId);
            }
            order.setVersion(order.getVersion() + 1);
            order.setLastSeq(currentSeq);
            stateStore.getOrderMap().put(orderId, order);
        }
    }

    private void processTradeLedger(OrderBook.TradeEvent t, long currentSeq, ActiveOrder takerOrder) {
        // 金融對沖邏輯：處理價格改進與進位保護
        long matchValueFloor = DecimalUtil.mulFloor(t.price, t.qty); // 用戶入帳用 Floor
        long matchValueCeil = DecimalUtil.mulCeil(t.price, t.qty);   // 用戶扣款用 Ceil

        // 判斷 Taker 是買方還是賣方
        if (takerOrder.getSide() == 0) { // Taker is BUYER
            // Taker 扣除 USDT 凍結 (按其下單價格計算的比例)，退還差額
            long takerFrozenForThisQty = DecimalUtil.mulCeil(takerOrder.getPrice(), t.qty);
            ledger.settlement(t.takerUserId, 2, currentSeq, matchValueCeil, takerFrozenForThisQty);
            ledger.addAvailable(t.takerUserId, 1, currentSeq, t.qty); // 得 BTC

            // Maker 扣除 BTC 凍結，得 USDT
            ledger.settlement(t.makerUserId, 1, currentSeq, t.qty, t.qty);
            ledger.addAvailable(t.makerUserId, 2, currentSeq, matchValueFloor);
        } else { // Taker is SELLER
            // Taker 扣除 BTC 凍結
            ledger.settlement(t.takerUserId, 1, currentSeq, t.qty, t.qty);
            ledger.addAvailable(t.takerUserId, 2, currentSeq, matchValueFloor); // 得 USDT

            // Maker (Buyer) 扣除 USDT 凍結 (Maker 當初掛單的價格)，退還差額
            ActiveOrder makerOrder = stateStore.getOrderMap().get(t.makerOrderId);
            long makerFrozenForThisQty = (makerOrder != null) ? DecimalUtil.mulCeil(makerOrder.getPrice(), t.qty) : matchValueCeil;
            ledger.settlement(t.makerUserId, 2, currentSeq, matchValueCeil, makerFrozenForThisQty);
            ledger.addAvailable(t.makerUserId, 1, currentSeq, t.qty); // 得 BTC
        }
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
