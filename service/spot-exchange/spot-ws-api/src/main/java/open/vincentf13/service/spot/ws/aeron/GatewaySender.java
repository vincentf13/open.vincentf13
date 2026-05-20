package open.vincentf13.service.spot.ws.aeron;

import com.lmax.disruptor.EventPoller;
import com.lmax.disruptor.RingBuffer;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.aeron.*;
import open.vincentf13.service.spot.infra.aeron.AeronConstants.AeronState;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import open.vincentf13.service.spot.infra.thread.Worker;
import open.vincentf13.service.spot.model.command.AbstractSbeModel;
import open.vincentf13.service.spot.sbe.*;
import open.vincentf13.service.spot.ws.wal.*;
import org.agrona.MutableDirectBuffer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.ByteOrder;

import static open.vincentf13.service.spot.infra.Constants.*;
import static open.vincentf13.service.spot.infra.aeron.AeronUtil.*;

/**
 * Gateway → Matching Engine 發送器
 *
 * Bypass 模式 (預設，-Dspot.wal.bypass=true)：Disruptor → Aeron 直送，零 WAL 開銷。
 * WAL 模式：由 {@link WalSender} 繼承並覆寫事件處理方法。
 *
 * Bypass 模式下此類直接作為 @Component 實例化（單一具體類，無繼承），
 * 讓 JIT 能完整 inline 整條 doWork → processEvents → sendFromEvent 熱路徑。
 */
@Slf4j
// 顯式 bean 名稱與 WalSender 對齊，NettyServer @DependsOn("gatewaySender") 在兩種模式都成立
@Component("gatewaySender")
@ConditionalOnProperty(name = "spot.wal.bypass", havingValue = "true")
public class GatewaySender extends Worker {

    // ===== Disruptor =====
    protected final RingBuffer<WalEvent> ringBuffer;
    protected final EventPoller<WalEvent> poller;

    // ===== Aeron =====
    protected Publication publication;
    private Subscription controlSub;
    protected AeronState currentState = AeronState.WAITING;

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
    protected int pollCount;
    protected long localWriteCount;
    private long localBackPressure;

    // ===== Bypass 模式狀態 =====
    private long bypassSeq = 0;

    // ===== 熱路徑採樣：減少 control poll / volatile read 頻率 =====
    private long iterCounter = 0;
    private static final long CONTROL_CHECK_MASK = 0xFF; // 每 256 次 iter 輪詢一次控制通道

    // ===== Diagnose =====
    protected static final boolean DIAGNOSE = Boolean.getBoolean("spot.diagnose");
    protected long sendDoneNs;

    @org.springframework.beans.factory.annotation.Autowired
    public GatewaySender(@SuppressWarnings("unused") io.aeron.Aeron aeron, RingBuffer<WalEvent> ringBuffer) {
        this("gateway-sender",
             MetricsKey.CPU_ID_WAL_SENDER, MetricsKey.CPU_ID_CURRENT_WAL_SENDER,
             MetricsKey.GATEWAY_WAL_SENDER_DUTY_CYCLE, ringBuffer);
    }

    protected GatewaySender(String name, long cpuIdKey, long currentCpuIdKey, long dutyCycleKey,
                            RingBuffer<WalEvent> ringBuffer) {
        super(name, cpuIdKey, currentCpuIdKey, dutyCycleKey);
        this.ringBuffer = ringBuffer;
        this.poller = ringBuffer.newPoller();
        ringBuffer.addGatingSequences(poller.getSequence());
    }

    @PostConstruct @Override public void start() { super.start(); }
    @PreDestroy @Override public void stop() { super.stop(); }

    @Override
    protected void onStart() {
        this.publication = AeronUtil.aeron().addPublication(AeronChannel.MATCHING_FLOW, AeronChannel.DATA_STREAM_ID);
        this.controlSub = AeronUtil.aeron().addSubscription(AeronChannel.REPORT_FLOW, AeronChannel.CONTROL_STREAM_ID);
        onSenderStart();
        log.info("[{}] 初始化完成", Thread.currentThread().getName());
    }

    protected void onSenderStart() {}

    // ===== 主迴圈 =====

    @Override
    protected int doWork() {
        // 熱路徑 (SENDING state)：只 processEvents，連線與控制通道檢查採樣
        if (currentState == AeronState.SENDING) {
            int work = processEvents();
            // 每 256 iter 輪詢一次控制通道 + 檢查連線狀態
            if ((++iterCounter & CONTROL_CHECK_MASK) == 0) {
                if (!publication.isConnected()) {
                    currentState = AeronState.WAITING;
                } else {
                    long beforeControl = DIAGNOSE ? System.nanoTime() : 0;
                    work += controlSub.poll(resumeHandler, AeronConstants.AERON_POLL_LIMIT);
                    if (DIAGNOSE) StaticMetricsHolder.recordLatency(MetricsKey.LATENCY_CONTROL_POLL, System.nanoTime() - beforeControl);
                }
            }
            return work;
        }

        // WAITING state 路徑 (低頻)
        long beforeControl = DIAGNOSE ? System.nanoTime() : 0;
        int work = controlSub.poll(resumeHandler, AeronConstants.AERON_POLL_LIMIT);
        if (DIAGNOSE) StaticMetricsHolder.recordLatency(MetricsKey.LATENCY_CONTROL_POLL, System.nanoTime() - beforeControl);
        work += drainWhileWaiting();
        return work;
    }

    // ===== Bypass: WAITING 丟棄 (防 Disruptor 堆積) =====

    private final EventPoller.Handler<WalEvent> discardHandler = (event, sequence, endOfBatch) -> {
        pollCount++;
        return true;
    };

    protected int drainWhileWaiting() {
        pollCount = 0;
        try { poller.poll(discardHandler); } catch (Exception ignored) {}
        return pollCount;
    }

    // ===== Bypass: SENDING Disruptor → Aeron 直送 =====

    private final EventPoller.Handler<WalEvent> bypassHandler = (event, sequence, endOfBatch) -> {
        long pollTimeNs = DIAGNOSE ? System.nanoTime() : 0;
        sendFromEvent(event, ++bypassSeq);
        StaticMetricsHolder.addCounter(MetricsKey.AERON_SEND_COUNT, 1);
        if (DIAGNOSE) recordTransportSubLatencies(event, pollTimeNs, System.nanoTime());
        pollCount++;
        return true;
    };

    protected int processEvents() {
        pollCount = 0;
        try { poller.poll(bypassHandler); } catch (Exception e) { log.error("[BYPASS-SENDER] failed", e); }
        if (pollCount > 0) localWriteCount += pollCount;
        return pollCount;
    }

    // ===== RESUME 握手 =====

    private final FragmentHandler resumeHandler = (buffer, offset, length, header) -> {
        if (buffer.getInt(offset, ByteOrder.LITTLE_ENDIAN) == MsgType.RESUME && currentState == AeronState.WAITING) {
            long walIndex = buffer.getLong(offset + AeronConstants.MSG_SEQ_OFFSET, ByteOrder.LITTLE_ENDIAN);
            onResume(walIndex);
            currentState = AeronState.SENDING;
        }
    };

    protected void onResume(long walIndex) {
        log.info("[BYPASS-SENDER] RESUME 握手成功，起始序號: {}", walIndex);
        bypassSeq = (walIndex == WAL_INDEX_NONE || walIndex == MSG_SEQ_NONE) ? 0 : walIndex;
    }

    // ===== SBE 編碼 + Aeron 發送 =====

    protected void sendFromEvent(WalEvent e, long walIndex) {
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

    /** 從 WAL binary 格式讀取並發送到 Aeron（RESUME replay 使用） */
    protected boolean readAndSendFromWire(net.openhft.chronicle.wire.Wire wire, long walIndex) {
        net.openhft.chronicle.bytes.Bytes<?> bytes = wire.bytes();
        curMsgType   = bytes.readInt();
        curGwTime    = bytes.readLong();
        curUserId    = bytes.readLong();
        curTimestamp  = bytes.readLong();
        curWalIndex  = walIndex;
        switch (curMsgType) {
            case MsgType.ORDER_CREATE -> {
                curSymbolId = bytes.readInt();
                curPrice = bytes.readLong();
                curQty = bytes.readLong();
                curSide = bytes.readByte();
                curClientOrderId = bytes.readLong();
            }
            case MsgType.ORDER_CANCEL -> curOrderId = bytes.readLong();
            case MsgType.DEPOSIT -> {
                curAssetId = bytes.readInt();
                curAmount = bytes.readLong();
            }
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

    protected boolean trySend(int totalLen) {
        while (running.get()) {
            int res = AeronUtil.send(publication, totalLen, aeronFiller);
            if (res == SEND_OK) {
                if (DIAGNOSE) sendDoneNs = System.nanoTime();
                if (curWalIndex == 1) log.info("[{}] 成功發送第一條消息 (seq=1)", Thread.currentThread().getName());
                return true;
            }
            if (res == SEND_BACKPRESSURE) { localBackPressure++; Thread.onSpinWait(); continue; }
            log.warn("[{}] send failed res={}, walIndex={}", Thread.currentThread().getName(), res, curWalIndex);
            if (res == SEND_DISCONNECTED) currentState = AeronState.WAITING;
            return false;
        }
        return false;
    }

    // ===== Diagnose 延遲記錄 =====

    protected void recordTransportSubLatencies(WalEvent e, long pollTimeNs, long doneNs) {
        StaticMetricsHolder.recordLatency(MetricsKey.LATENCY_NETTY_PROCESS, e.publishTimeNs - e.arrivalTimeNs);
        StaticMetricsHolder.recordLatency(MetricsKey.LATENCY_DISRUPTOR_WAIT, pollTimeNs - e.publishTimeNs);
        StaticMetricsHolder.recordLatency(MetricsKey.LATENCY_SENDER_ENCODE, doneNs - pollTimeNs);
        StaticMetricsHolder.recordLatency(MetricsKey.LATENCY_GATEWAY_TOTAL, sendDoneNs - e.arrivalTimeNs);
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
        onSenderStop();
        if (publication != null) publication.close();
        if (controlSub != null) controlSub.close();
        log.info("[{}] 已停止", Thread.currentThread().getName());
    }

    protected void onSenderStop() {
        try { poller.poll(discardHandler); } catch (Exception ignored) {}
    }
}
