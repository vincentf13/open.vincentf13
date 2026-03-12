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
 特性：
 1. 單執行緒模型：消除鎖競爭，保證極致的確定性。
 2. Event Sourcing：透過重播 Command WAL 恢復內存狀態。
 3. Zero-GC & Low-Latency：採用 SBE 編解碼與堆外內存映射。
 */
@Slf4j
@Component
public class Engine extends Worker {
    private final Ledger ledger;
    // 內存訂單簿集合 (symbolId -> OrderBook)
    private final Int2ObjectHashMap<OrderBook> books = new Int2ObjectHashMap<>();
    // 活躍訂單索引，用於快速檢索與狀態同步
    private final Long2ObjectHashMap<Order> activeOrderIndex = new Long2ObjectHashMap<>();
    
    private final Progress progress = new Progress();
    private ExcerptTailer tailer;
    private boolean isReplaying = false;

    // SBE 解碼器與暫存區
    private final OrderCreateDecoder createDecoder = new OrderCreateDecoder();
    private final UnsafeBuffer payloadBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(512);
    
    // SBE 編碼器與發送緩衝區
    private final ExecutionReportEncoder executionEncoder = new ExecutionReportEncoder();
    private final Bytes<ByteBuffer> outboundBytes = Bytes.elasticByteBuffer(1024);
    private final UnsafeBuffer outboundSbeBuffer = new UnsafeBuffer(0, 0);

    public Engine(Ledger ledger) {
        this.ledger = ledger;
    }

    /** 初始化並啟動工作執行緒 */
    @PostConstruct public void init() { start("core-matching-engine"); }

    /** 
     工作啟動準備：
     1. 加載 Command Queue Tailer
     2. 從 Metadata 恢復處理位點、訂單 ID 與成交 ID 計數器
     3. 遍歷 Chronicle Map 重建內存訂單簿索引
     4. 若有進度位點，自動跳轉執行「熱重播」
     */
    @Override
    protected void onStart() {
        this.tailer = Storage.self().commandQueue().createTailer();
        
        Progress saved = Storage.self().metadata().get(MetaDataKey.PK_CORE_ENGINE);
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
            log.info("引擎進入重播模式，起始位點: {}", progress.getLastProcessedSeq());
        }
    }

    /** 
     核心工作循環：
     1. 從 Command Queue 讀取下一個指令文檔
     2. 提取 GW 原始序號與指令類型
     3. 分發處理邏輯，使用 gwSeq 維護帳務與狀態一致性
     */
    @Override
    protected int doWork() {
        boolean handled = tailer.readDocument(wire -> {
            long seq = tailer.index();
            int msgType = wire.read(ChronicleWireKey.msgType).int32();
            // 讀取 Gateway 原始序號
            long gwSeq = wire.read(ChronicleWireKey.gwSeq).int64();
            
            if (isReplaying && seq >= tailer.queue().lastIndex()) {
                isReplaying = false;
                log.info("重播結束，引擎切換至實時處理模式");
            }

            // 零拷貝讀取 Payload 內存位址
            reusableBytes.clear(); 
            wire.read(ChronicleWireKey.payload).bytes(reusableBytes);
            payloadBuffer.wrap(reusableBytes.addressForRead(reusableBytes.readPosition()), (int)reusableBytes.readRemaining());

            // 業務邏輯分發：將 gwSeq 傳入作為冪等性標籤
            if (msgType == MsgType.AUTH) handleAuth(wire, gwSeq);
            else if (msgType == MsgType.ORDER_CREATE) dispatchOrderCreate(gwSeq);

            // 更新並持久化當前進度位點 (仍以 Command Queue 的本地 Seq 為準，以便重播跳轉)
            progress.setLastProcessedSeq(seq);
            Storage.self().metadata().put(MetaDataKey.PK_CORE_ENGINE, progress);
        });
        
        if (!handled && isReplaying) isReplaying = false;
        return handled ? 1 : 0;
    }

    /** 處理訂單創建入口 (含冪等性檢查) */
    private void dispatchOrderCreate(long gwSeq) {
        SbeCodec.decode(payloadBuffer, 0, createDecoder);
        String cid = createDecoder.clientOrderId();
        CidKey key = new CidKey(createDecoder.userId(), cid);
        
        // 冪等性校驗：若 clientOrderId 已存在
        Long resId = Storage.self().cids().get(key);
        if (resId != null) {
            if (!isReplaying) resendReport(resId, createDecoder.userId(), cid, createDecoder.timestamp());
            return;
        }
        
        // 執行核心下單邏輯，使用 gwSeq 標記狀態
        Storage.self().cids().put(key, handleOrderCreate(createDecoder, gwSeq, cid));
    }

    /** 
     核心限價單處理邏輯
     */
    private long handleOrderCreate(OrderCreateDecoder sbe, long gwSeq, String cid) {
        long ts = sbe.timestamp();
        long cost = (sbe.side() == Side.BUY) ? DecimalUtil.mulCeil(sbe.price(), sbe.qty()) : sbe.qty();
        int aid = (sbe.side() == Side.BUY) ? Asset.USDT : Asset.BTC;

        // 步驟 1: 預扣資產，使用 gwSeq 確保 Ledger 冪等
        if (!ledger.tryFreeze(sbe.userId(), aid, gwSeq, cost)) {
            sendReport(sbe.userId(), 0, cid, OrderStatus.REJECTED, 0, 0, 0, 0, ts);
            return ID_REJECTED;
        }

        // 步驟 2: 分配訂單 ID 並初始化
        long oid = progress.getOrderIdCounter(); progress.setOrderIdCounter(oid + 1);
        Order o = new Order();
        o.setOrderId(oid); o.setClientOrderId(cid); o.setUserId(sbe.userId());
        o.setSymbolId((int)sbe.symbolId()); o.setPrice(sbe.price()); o.setQty(sbe.qty());
        o.setSide((byte)(sbe.side() == Side.BUY ? 0 : 1)); o.setStatus((byte)0);
        o.setVersion(1); 
        o.setLastSeq(gwSeq); // 使用 GW 序號標記

        activeOrderIndex.put(oid, o); 
        persistOrder(o);

        // 步驟 3: 進入訂單簿撮合
        List<OrderBook.TradeEvent> trades = books.computeIfAbsent(o.getSymbolId(), OrderBook::new).match(o);
        
        // 步驟 4: 處理成交事件
        for (OrderBook.TradeEvent t : trades) {
            long tid = progress.getTradeIdCounter(); progress.setTradeIdCounter(tid + 1);
            persistTrade(t, tid, sbe.timestamp(), gwSeq);
            processTradeLedger(t, gwSeq, o);
            syncOrder(t.makerOrderId, gwSeq);
            sendReport(t.makerUserId, t.makerOrderId, "", OrderStatus.PARTIALLY_FILLED, t.price, t.qty, 0, 0, sbe.timestamp());
        }

        // 步驟 5: 同步 Taker 狀態
        syncOrder(oid, gwSeq);
        OrderStatus st = (o.getStatus() == 2) ? OrderStatus.FILLED : OrderStatus.NEW;
        sendReport(sbe.userId(), oid, cid, st, 0, 0, o.getFilled(), 0, ts);
        
        return oid;
    }

    /** 更新訂單狀態並維持持久化一致性 */
    private void syncOrder(long id, long gwSeq) {
        Order o = activeOrderIndex.get(id);
        if (o != null) {
            if (o.getFilled() == o.getQty()) { 
                o.setStatus((byte)2); 
                activeOrderIndex.remove(id); 
            }
            o.setVersion(o.getVersion() + 1); 
            o.setLastSeq(gwSeq); 
            persistOrder(o);
        }
    }

    /** 持久化訂單至 Chronicle Map */
    private void persistOrder(Order o) {
        Order ex = Storage.self().orders().get(o.getOrderId());
        // 核心防禦：基於 GW 序號判斷是否寫入，確保狀態不回退
        if (ex == null || ex.getLastSeq() < o.getLastSeq()) {
            Storage.self().orders().put(o.getOrderId(), o);
            if (o.getStatus() < 2) Storage.self().activeOrders().put(o.getOrderId(), true);
            else Storage.self().activeOrders().remove(o.getOrderId());
        }
    }

    /** 成交後的帳務結算與溢價退款 */
    private void processTradeLedger(OrderBook.TradeEvent t, long gwSeq, Order taker) {
        long floor = DecimalUtil.mulFloor(t.price, t.qty);
        long ceil = DecimalUtil.mulCeil(t.price, t.qty);
        
        if (taker.getSide() == 0) { // Taker 買入
            ledger.tradeSettleWithRefund(t.takerUserId, Asset.USDT, ceil, DecimalUtil.mulCeil(taker.getPrice(), t.qty), Asset.BTC, t.qty, gwSeq);
            ledger.tradeSettle(t.makerUserId, Asset.BTC, t.qty, Asset.USDT, floor, gwSeq);
        } else { // Taker 賣出
            ledger.tradeSettle(t.takerUserId, Asset.BTC, t.qty, Asset.USDT, floor, gwSeq);
            Order m = activeOrderIndex.get(t.makerOrderId);
            long mCeil = (m != null) ? DecimalUtil.mulCeil(m.getPrice(), t.qty) : ceil;
            ledger.tradeSettleWithRefund(t.makerUserId, Asset.USDT, ceil, mCeil, Asset.BTC, t.qty, gwSeq);
        }
        
        if (ceil > floor) {
            ledger.addAvailable(PLATFORM_USER_ID, Asset.USDT, gwSeq, ceil - floor);
        }
    }

    /** 封裝並發送成交回報 (SBE 二進位格式) */
    private void sendReport(long uid, long oid, String cid, OrderStatus s, long lp, long lq, long cq, long ap, long ts) {
        if (isReplaying) return;
        outboundBytes.clear();
        outboundSbeBuffer.wrap(outboundBytes.addressForWrite(0), (int)outboundBytes.realCapacity());
        int len = SbeCodec.encode(outboundSbeBuffer, 0, executionEncoder.timestamp(ts).userId(uid).orderId(oid).status(s).lastPrice(lp).lastQty(lq).cumQty(cq).avgPrice(ap).clientOrderId(cid));
        outboundBytes.writePosition(len);
        Storage.self().resultQueue().acquireAppender().writeDocument(wire -> {
            wire.write(ChronicleWireKey.msgType).int32(executionEncoder.sbeTemplateId());
            wire.write(ChronicleWireKey.payload).bytes(outboundBytes);
        });
    }

    /** 重發回報 */
    private void resendReport(long oid, long uid, String cid, long ts) {
        if (oid == ID_REJECTED) {
            sendReport(uid, 0, cid, OrderStatus.REJECTED, 0, 0, 0, 0, ts);
        } else {
            Order o = Storage.self().orders().get(oid);
            if (o != null) {
                OrderStatus s = (o.getStatus() == 2) ? OrderStatus.FILLED : 
                                (o.getStatus() == 3) ? OrderStatus.REJECTED : 
                                (o.getStatus() == 1) ? OrderStatus.PARTIALLY_FILLED : OrderStatus.NEW;
                sendReport(o.getUserId(), o.getOrderId(), o.getClientOrderId(), s, 0, 0, o.getFilled(), 0, ts);
            }
        }
    }

    /** 持久化成交歷史 */
    private void persistTrade(OrderBook.TradeEvent t, long tid, long ts, long gwSeq) {
        Trade r = Storage.self().trades().get(tid);
        if (r == null || r.getLastSeq() < gwSeq) {
            r = new Trade(); 
            r.setTradeId(tid); r.setOrderId(t.makerOrderId); r.setPrice(t.price); r.setQty(t.qty); r.setTime(ts); r.setLastSeq(gwSeq);
            Storage.self().trades().put(tid, r);
        }
    }

    /** 處理系統認證訊息 */
    private void handleAuth(net.openhft.chronicle.wire.WireIn wire, long gwSeq) {
        long userId = wire.read(ChronicleWireKey.userId).int64();
        ledger.initBalance(userId, Asset.BTC, gwSeq); 
        ledger.initBalance(userId, Asset.USDT, gwSeq);
        
        if (isReplaying) return;
        
        Storage.self().resultQueue().acquireAppender().writeDocument(w -> {
            w.write(ChronicleWireKey.topic).text("auth.success");
            w.write(ChronicleWireKey.userId).int64(userId);
        });
    }

    @Override 
    protected void onStop() { 
        reusableBytes.releaseLast(); 
        outboundBytes.releaseLast(); 
    }
}
