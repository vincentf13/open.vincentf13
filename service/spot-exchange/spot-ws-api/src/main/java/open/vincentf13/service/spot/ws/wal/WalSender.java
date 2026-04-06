package open.vincentf13.service.spot.ws.wal;

import com.lmax.disruptor.EventPoller;
import com.lmax.disruptor.RingBuffer;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.Wire;
import open.vincentf13.service.spot.infra.aeron.*;
import open.vincentf13.service.spot.infra.aeron.AeronConstants.AeronState;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.chronicle.WalField;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import open.vincentf13.service.spot.infra.thread.Worker;
import open.vincentf13.service.spot.infra.util.PreTouchUtil;
import open.vincentf13.service.spot.model.command.AbstractSbeModel;
import open.vincentf13.service.spot.sbe.*;
import org.agrona.MutableDirectBuffer;
import org.springframework.stereotype.Component;

import java.nio.ByteOrder;

import static open.vincentf13.service.spot.infra.Constants.*;
import static open.vincentf13.service.spot.infra.aeron.AeronUtil.*;

/**
 * WAL + Aeron 合併發送器 (WalSender)
 *
 * 合併原 WalWriter + AeronSender 為單一 worker thread，消除跨線程 handoff：
 *   舊: Netty → Disruptor → WalWriter → Chronicle Queue → AeronSender → Aeron (4 handoffs)
 *   新: Netty → Disruptor → WalSender (寫 WAL + 送 Aeron) → Aeron          (3 handoffs)
 *
 * 寫入順序保證：先寫 WAL 成功拿到 walIndex，再送 Aeron。
 * Matching Engine 的 RESUME 握手 + WAL replay 機制完整保留。
 */
@Slf4j
@Component
public class WalSender extends Worker {

    // ===== Disruptor (from WalWriter) =====
    private final RingBuffer<WalEvent> ringBuffer;
    private final EventPoller<WalEvent> poller;

    // ===== Chronicle Queue (from WalWriter) =====
    private final ChronicleQueue wal;
    private ExcerptAppender appender;

    // ===== Aeron (from AeronSender) =====
    private Publication publication;
    private Subscription controlSub;
    private ExcerptTailer replayTailer;
    private AeronState currentState = AeronState.WAITING;
    private long resumeSkipIndex = Long.MIN_VALUE;
    private boolean replaying = false;

    // ===== SBE 編碼器 (單線程獨佔) =====
    private final MessageHeaderEncoder sbeHeaderEncoder = new MessageHeaderEncoder();
    private final OrderCreateEncoder orderCreateEncoder = new OrderCreateEncoder();
    private final OrderCancelEncoder orderCancelEncoder = new OrderCancelEncoder();
    private final DepositEncoder depositEncoder = new DepositEncoder();
    private final AuthEncoder authEncoder = new AuthEncoder();

    // ===== 當前訊息狀態 (單線程，用 fields 取代 per-call lambda 捕獲) =====
    private int curMsgType;
    private long curWalIndex, curGwTime, curTimestamp, curUserId;
    private int curSymbolId, curAssetId;
    private long curPrice, curQty, curClientOrderId, curOrderId, curAmount;
    private byte curSide;

    // ===== Metrics =====
    private int pollCount;
    private long localWriteCount;
    private long localBackPressure;

    public WalSender(@SuppressWarnings("unused") io.aeron.Aeron aeron, RingBuffer<WalEvent> ringBuffer) {
        super("wal-sender",
              MetricsKey.CPU_ID_WAL_SENDER, MetricsKey.CPU_ID_CURRENT_WAL_SENDER,
              MetricsKey.GATEWAY_WAL_SENDER_DUTY_CYCLE);
        this.wal = Storage.self().gatewaySenderWal();
        this.ringBuffer = ringBuffer;
        this.poller = ringBuffer.newPoller();
        ringBuffer.addGatingSequences(poller.getSequence());
    }

    @PostConstruct @Override public void start() { super.start(); }
    @PreDestroy @Override public void stop() { super.stop(); }

    @Override
    protected void onStart() {
        PreTouchUtil.touchDirectory(new java.io.File(ChronicleMapEnum.WAL_BASE_DIR));
        this.appender = wal.acquireAppender();
        this.publication = AeronUtil.aeron().addPublication(AeronChannel.MATCHING_FLOW, AeronChannel.DATA_STREAM_ID);
        this.controlSub = AeronUtil.aeron().addSubscription(AeronChannel.REPORT_FLOW, AeronChannel.CONTROL_STREAM_ID);
        this.replayTailer = wal.createTailer();
        log.info("[WAL-SENDER] 初始化完成，合併 WAL 寫入 + Aeron 發送");
    }

    private static final boolean BYPASS_WAL = Boolean.getBoolean("spot.wal.bypass");
    private static final boolean DIAGNOSE = Boolean.getBoolean("spot.diagnose");
    private long bypassSeq = 0;

    // ===== 主迴圈 =====

    @Override
    protected int doWork() {
        if (currentState == AeronState.SENDING && !publication.isConnected()) currentState = AeronState.WAITING;

        long beforeControl = DIAGNOSE ? System.nanoTime() : 0;
        int work = controlSub.poll(resumeHandler, AeronConstants.AERON_POLL_LIMIT);
        if (DIAGNOSE) StaticMetricsHolder.recordLatency(MetricsKey.LATENCY_CONTROL_POLL, System.nanoTime() - beforeControl);

        if (currentState == AeronState.WAITING) {
            // WAITING：排空 Disruptor 防止堆積。WAL 模式寫入持久化，Bypass 模式丟棄。
            work += BYPASS_WAL ? drainAndDiscard() : drainToWalOnly();
            return work;
        }

        if (replaying && !BYPASS_WAL) {
            work += replayFromWal();
            work += drainToWalOnly();
            return work;
        }

        // LIVE (或 BYPASS)
        work += BYPASS_WAL ? processBypass() : processLive();
        return work;
    }

    // ===== WAITING 模式：只寫 WAL =====

    private final EventPoller.Handler<WalEvent> walOnlyHandler = (event, sequence, endOfBatch) -> {
        writeToWal(event);
        pollCount++;
        return true;
    };

    private int drainToWalOnly() {
        pollCount = 0;
        try { poller.poll(walOnlyHandler); } catch (Exception e) { log.error("[WAL-SENDER] WAL write failed", e); }
        if (pollCount > 0) localWriteCount += pollCount;
        return pollCount;
    }

    // ===== REPLAY 模式：從 WAL 追趕 → Aeron =====

    private int replayFromWal() {
        int count = 0;
        for (int i = 0; i < AeronConstants.WAL_BATCH_SIZE; i++) {
            try (var dc = replayTailer.readingDocument()) {
                if (!dc.isPresent()) {
                    replaying = false;
                    log.info("[WAL-SENDER] WAL replay 完成，切換到 live 模式");
                    break;
                }
                long walIndex = dc.index();
                if (walIndex == resumeSkipIndex) { resumeSkipIndex = Long.MIN_VALUE; continue; }
                Wire wire = dc.wire();
                if (wire == null) break;
                if (!readAndSendFromWire(wire, walIndex)) break;
                StaticMetricsHolder.addCounter(MetricsKey.AERON_SEND_COUNT, 1);
                count++;
            }
        }
        return count;
    }

    // ===== LIVE 模式：Disruptor → WAL → Aeron =====

    private final EventPoller.Handler<WalEvent> liveHandler = (event, sequence, endOfBatch) -> {
        long pollTimeNs = DIAGNOSE ? System.nanoTime() : 0;
        long walIndex = writeToWal(event);
        if (walIndex >= 0) {
            sendFromEvent(event, walIndex);
            StaticMetricsHolder.addCounter(MetricsKey.AERON_SEND_COUNT, 1);
            if (DIAGNOSE) recordTransportSubLatencies(event, pollTimeNs, System.nanoTime());
        }
        pollCount++;
        return true;
    };

    private int processLive() {
        pollCount = 0;
        try { poller.poll(liveHandler); } catch (Exception e) { log.error("[WAL-SENDER] live processing failed", e); }
        if (pollCount > 0) localWriteCount += pollCount;
        return pollCount;
    }

    // ===== BYPASS WAITING：排空 Disruptor 丟棄（防 RingBuffer 堆積）=====

    private final EventPoller.Handler<WalEvent> discardHandler = (event, sequence, endOfBatch) -> {
        pollCount++;
        return true;
    };

    private int drainAndDiscard() {
        pollCount = 0;
        try { poller.poll(discardHandler); } catch (Exception ignored) {}
        return pollCount;
    }

    // ===== BYPASS 模式：跳過 WAL，Disruptor → Aeron 直送 =====

    private final EventPoller.Handler<WalEvent> bypassHandler = (event, sequence, endOfBatch) -> {
        long pollTimeNs = DIAGNOSE ? System.nanoTime() : 0;
        sendFromEvent(event, ++bypassSeq);
        StaticMetricsHolder.addCounter(MetricsKey.AERON_SEND_COUNT, 1);
        if (DIAGNOSE) recordTransportSubLatencies(event, pollTimeNs, System.nanoTime());
        pollCount++;
        return true;
    };

    private int processBypass() {
        pollCount = 0;
        try { poller.poll(bypassHandler); } catch (Exception e) { log.error("[WAL-SENDER] bypass failed", e); }
        if (pollCount > 0) localWriteCount += pollCount;
        return pollCount;
    }

    // ===== WAL 寫入 (from WalWriter) =====

    private long writeToWal(WalEvent e) {
        try (var dc = appender.writingDocument()) {
            Wire wire = dc.wire();
            if (wire == null) return -1;

            wire.write(WalField.msgType).int32(e.msgType);
            wire.write(WalField.gwTime).int64(e.arrivalTimeNs);
            wire.write(WalField.userId).int64(e.userId);
            wire.write(WalField.timestamp).int64(e.timestamp);

            switch (e.msgType) {
                case MsgType.ORDER_CREATE -> {
                    WalOrderCreate oc = e.orderCreate;
                    wire.write(WalField.symbolId).int32(oc.symbolId);
                    wire.write(WalField.price).int64(oc.price);
                    wire.write(WalField.qty).int64(oc.qty);
                    wire.write(WalField.side).int8(oc.side);
                    wire.write(WalField.clientOrderId).int64(oc.clientOrderId);
                }
                case MsgType.ORDER_CANCEL -> wire.write(WalField.orderId).int64(e.orderCancel.orderId);
                case MsgType.DEPOSIT -> {
                    WalDeposit d = e.deposit;
                    wire.write(WalField.assetId).int32(d.assetId);
                    wire.write(WalField.amount).int64(d.amount);
                }
            }
            return dc.index();
        }
    }

    // ===== Aeron 發送：直接從 WalEvent 編碼 SBE (LIVE 模式) =====

    private void sendFromEvent(WalEvent e, long walIndex) {
        curMsgType = e.msgType;
        curWalIndex = walIndex;
        curGwTime = e.arrivalTimeNs;
        curTimestamp = e.timestamp;
        curUserId = e.userId;
        switch (e.msgType) {
            case MsgType.ORDER_CREATE -> {
                WalOrderCreate oc = e.orderCreate;
                curSymbolId = oc.symbolId; curPrice = oc.price; curQty = oc.qty;
                curSide = oc.side; curClientOrderId = oc.clientOrderId;
            }
            case MsgType.ORDER_CANCEL -> curOrderId = e.orderCancel.orderId;
            case MsgType.DEPOSIT -> { curAssetId = e.deposit.assetId; curAmount = e.deposit.amount; }
        }
        trySend(sbeBodyLength(e.msgType));
    }

    // ===== Aeron 發送：從 BinaryWire 解碼 (REPLAY 模式) =====

    private boolean readAndSendFromWire(Wire wire, long walIndex) {
        curMsgType   = wire.read(WalField.msgType).int32();
        curGwTime    = wire.read(WalField.gwTime).int64();
        curUserId    = wire.read(WalField.userId).int64();
        curTimestamp  = wire.read(WalField.timestamp).int64();
        curWalIndex  = walIndex;
        switch (curMsgType) {
            case MsgType.ORDER_CREATE -> {
                curSymbolId = wire.read(WalField.symbolId).int32();
                curPrice = wire.read(WalField.price).int64();
                curQty = wire.read(WalField.qty).int64();
                curSide = wire.read(WalField.side).int8();
                curClientOrderId = wire.read(WalField.clientOrderId).int64();
            }
            case MsgType.ORDER_CANCEL -> curOrderId = wire.read(WalField.orderId).int64();
            case MsgType.DEPOSIT -> { curAssetId = wire.read(WalField.assetId).int32(); curAmount = wire.read(WalField.amount).int64(); }
            default -> { return true; }
        }
        return trySend(sbeBodyLength(curMsgType));
    }

    private int sbeBodyLength(int msgType) {
        return AbstractSbeModel.BODY_OFFSET + switch (msgType) {
            case MsgType.ORDER_CREATE -> OrderCreateEncoder.BLOCK_LENGTH;
            case MsgType.ORDER_CANCEL -> OrderCancelEncoder.BLOCK_LENGTH;
            case MsgType.DEPOSIT -> DepositEncoder.BLOCK_LENGTH;
            case MsgType.AUTH -> AuthEncoder.BLOCK_LENGTH;
            default -> 0;
        };
    }

    // ===== 預分配 Aeron handler — zero allocation per call =====

    private final AeronUtil.AeronHandler aeronFiller = this::fillAeronBuffer;

    private void fillAeronBuffer(MutableDirectBuffer buf, int off) {
        buf.putInt(off + AbstractSbeModel.TYPE_OFFSET, curMsgType, ByteOrder.LITTLE_ENDIAN);
        buf.putInt(off + 4, 0, ByteOrder.LITTLE_ENDIAN);
        buf.putLong(off + AbstractSbeModel.SEQ_OFFSET, curWalIndex, ByteOrder.LITTLE_ENDIAN);
        buf.putLong(off + AbstractSbeModel.GATEWAY_TIME_OFFSET, curGwTime, ByteOrder.LITTLE_ENDIAN);

        int sbeOff = off + AbstractSbeModel.SBE_HEADER_OFFSET;
        switch (curMsgType) {
            case MsgType.ORDER_CREATE ->
                orderCreateEncoder.wrapAndApplyHeader(buf, sbeOff, sbeHeaderEncoder)
                    .timestamp(curTimestamp).userId(curUserId).symbolId(curSymbolId)
                    .price(curPrice).qty(curQty).side(Side.get((short) curSide)).clientOrderId(curClientOrderId);
            case MsgType.ORDER_CANCEL ->
                orderCancelEncoder.wrapAndApplyHeader(buf, sbeOff, sbeHeaderEncoder)
                    .timestamp(curTimestamp).userId(curUserId).orderId(curOrderId);
            case MsgType.DEPOSIT ->
                depositEncoder.wrapAndApplyHeader(buf, sbeOff, sbeHeaderEncoder)
                    .timestamp(curTimestamp).userId(curUserId).assetId(curAssetId).amount(curAmount);
            case MsgType.AUTH ->
                authEncoder.wrapAndApplyHeader(buf, sbeOff, sbeHeaderEncoder)
                    .timestamp(curTimestamp).userId(curUserId);
        }
    }

    private boolean trySend(int totalLen) {
        while (running.get()) {
            int res = AeronUtil.send(publication, totalLen, aeronFiller);
            if (res == SEND_OK) {
                if (curWalIndex == 1) log.info("[WAL-SENDER] 成功發送第一條消息 (seq=1)");
                return true;
            }
            if (res == SEND_BACKPRESSURE) { localBackPressure++; Thread.onSpinWait(); continue; }
            log.warn("[WAL-SENDER] send failed res={}, walIndex={}", res, curWalIndex);
            if (res == SEND_DISCONNECTED) currentState = AeronState.WAITING;
            return false;
        }
        return false;
    }

    // ===== RESUME 握手 =====

    private final FragmentHandler resumeHandler = (buffer, offset, length, header) -> {
        if (buffer.getInt(offset, ByteOrder.LITTLE_ENDIAN) == MsgType.RESUME && currentState == AeronState.WAITING) {
            long walIndex = buffer.getLong(offset + AeronConstants.MSG_SEQ_OFFSET, ByteOrder.LITTLE_ENDIAN);
            log.info("[WAL-SENDER] RESUME 握手成功，恢復位點: {}，bypass={}", walIndex, BYPASS_WAL);

            if (BYPASS_WAL) {
                bypassSeq = (walIndex == WAL_INDEX_NONE || walIndex == MSG_SEQ_NONE) ? 0 : walIndex;
            } else {
                if (walIndex == WAL_INDEX_NONE || walIndex == MSG_SEQ_NONE || !replayTailer.moveToIndex(walIndex)) {
                    replayTailer.toStart();
                    resumeSkipIndex = Long.MIN_VALUE;
                } else {
                    resumeSkipIndex = walIndex;
                }
                replaying = true;
            }
            currentState = AeronState.SENDING;
        }
    };

    // ===== Transport 子段延遲 (僅 -Dspot.diagnose=true) =====

    private void recordTransportSubLatencies(WalEvent e, long pollTimeNs, long doneNs) {
        // netty_process: gwTimeNs → publishTimeNs (Netty 解碼 + Disruptor publish)
        StaticMetricsHolder.recordLatency(MetricsKey.LATENCY_NETTY_PROCESS, e.publishTimeNs - e.arrivalTimeNs);
        // disruptor_wait: publishTimeNs → pollTimeNs (事件在 RingBuffer 等待)
        StaticMetricsHolder.recordLatency(MetricsKey.LATENCY_DISRUPTOR_WAIT, pollTimeNs - e.publishTimeNs);
        // sender_encode: pollTimeNs → doneNs (WAL write + SBE encode + Aeron send)
        StaticMetricsHolder.recordLatency(MetricsKey.LATENCY_SENDER_ENCODE, doneNs - pollTimeNs);
    }

    // ===== Metrics =====

    @Override
    protected void onMetricsReport() {
        if (localWriteCount > 0) {
            StaticMetricsHolder.addCounter(MetricsKey.GATEWAY_WAL_WRITE_COUNT, localWriteCount);
            localWriteCount = 0;
        }
        if (localBackPressure > 0) {
            StaticMetricsHolder.addCounter(MetricsKey.AERON_BACKPRESSURE, localBackPressure);
            localBackPressure = 0;
        }
    }

    @Override
    protected void onStop() {
        // 排空 Disruptor 殘留事件
        try { poller.poll(BYPASS_WAL ? discardHandler : walOnlyHandler); } catch (Exception e) { log.warn("[WAL-SENDER] drain failed", e); }
        if (publication != null) publication.close();
        if (controlSub != null) controlSub.close();
        log.info("[WAL-SENDER] 已停止");
    }
}
