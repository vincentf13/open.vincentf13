package open.vincentf13.service.spot.ws.aeron;

import com.lmax.disruptor.EventPoller;
import com.lmax.disruptor.RingBuffer;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import lombok.extern.slf4j.Slf4j;
import open.vincentf13.service.spot.infra.aeron.*;
import open.vincentf13.service.spot.infra.aeron.AeronConstants.AeronState;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import open.vincentf13.service.spot.infra.thread.Worker;
import open.vincentf13.service.spot.model.command.AbstractSbeModel;
import open.vincentf13.service.spot.sbe.*;
import open.vincentf13.service.spot.ws.wal.*;
import org.agrona.MutableDirectBuffer;

import java.nio.ByteOrder;

import static open.vincentf13.service.spot.infra.Constants.*;
import static open.vincentf13.service.spot.infra.aeron.AeronUtil.*;

/**
 * Gateway → Matching Engine 發送器基類
 *
 * 封裝 Aeron 連線管理、SBE 編碼、RESUME 握手、Disruptor polling 等共用邏輯。
 * 子類只需實現事件處理策略：
 * - {@link WalSender}: WAL 持久化 + Aeron 發送 (生產模式)
 * - {@link BypassSender}: 跳過 WAL，Disruptor → Aeron 直送 (壓測模式)
 */
@Slf4j
public abstract class GatewaySender extends Worker {

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

    // ===== Diagnose =====
    protected static final boolean DIAGNOSE = Boolean.getBoolean("spot.diagnose");
    protected long sendDoneNs;

    protected GatewaySender(String name, long cpuIdKey, long currentCpuIdKey, long dutyCycleKey,
                            RingBuffer<WalEvent> ringBuffer) {
        super(name, cpuIdKey, currentCpuIdKey, dutyCycleKey);
        this.ringBuffer = ringBuffer;
        this.poller = ringBuffer.newPoller();
        ringBuffer.addGatingSequences(poller.getSequence());
    }

    @Override
    protected void onStart() {
        this.publication = AeronUtil.aeron().addPublication(AeronChannel.MATCHING_FLOW, AeronChannel.DATA_STREAM_ID);
        this.controlSub = AeronUtil.aeron().addSubscription(AeronChannel.REPORT_FLOW, AeronChannel.CONTROL_STREAM_ID);
        onSenderStart();
        log.info("[{}] 初始化完成", Thread.currentThread().getName());
    }

    protected abstract void onSenderStart();

    // ===== 主迴圈 =====

    @Override
    protected int doWork() {
        if (currentState == AeronState.SENDING && !publication.isConnected()) currentState = AeronState.WAITING;

        long beforeControl = DIAGNOSE ? System.nanoTime() : 0;
        int work = controlSub.poll(resumeHandler, AeronConstants.AERON_POLL_LIMIT);
        if (DIAGNOSE) StaticMetricsHolder.recordLatency(MetricsKey.LATENCY_CONTROL_POLL, System.nanoTime() - beforeControl);

        if (currentState == AeronState.WAITING) {
            work += drainWhileWaiting();
            return work;
        }

        work += processEvents();
        return work;
    }

    protected abstract int drainWhileWaiting();

    protected abstract int processEvents();

    // ===== RESUME 握手 =====

    private final FragmentHandler resumeHandler = (buffer, offset, length, header) -> {
        if (buffer.getInt(offset, ByteOrder.LITTLE_ENDIAN) == MsgType.RESUME && currentState == AeronState.WAITING) {
            long walIndex = buffer.getLong(offset + AeronConstants.MSG_SEQ_OFFSET, ByteOrder.LITTLE_ENDIAN);
            onResume(walIndex);
            currentState = AeronState.SENDING;
        }
    };

    protected abstract void onResume(long walIndex);

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

    protected boolean readAndSendFromWire(net.openhft.chronicle.wire.Wire wire, long walIndex) {
        curMsgType   = wire.read(open.vincentf13.service.spot.infra.chronicle.WalField.msgType).int32();
        curGwTime    = wire.read(open.vincentf13.service.spot.infra.chronicle.WalField.gwTime).int64();
        curUserId    = wire.read(open.vincentf13.service.spot.infra.chronicle.WalField.userId).int64();
        curTimestamp  = wire.read(open.vincentf13.service.spot.infra.chronicle.WalField.timestamp).int64();
        curWalIndex  = walIndex;
        switch (curMsgType) {
            case MsgType.ORDER_CREATE -> {
                curSymbolId = wire.read(open.vincentf13.service.spot.infra.chronicle.WalField.symbolId).int32();
                curPrice = wire.read(open.vincentf13.service.spot.infra.chronicle.WalField.price).int64();
                curQty = wire.read(open.vincentf13.service.spot.infra.chronicle.WalField.qty).int64();
                curSide = wire.read(open.vincentf13.service.spot.infra.chronicle.WalField.side).int8();
                curClientOrderId = wire.read(open.vincentf13.service.spot.infra.chronicle.WalField.clientOrderId).int64();
            }
            case MsgType.ORDER_CANCEL -> curOrderId = wire.read(open.vincentf13.service.spot.infra.chronicle.WalField.orderId).int64();
            case MsgType.DEPOSIT -> {
                curAssetId = wire.read(open.vincentf13.service.spot.infra.chronicle.WalField.assetId).int32();
                curAmount = wire.read(open.vincentf13.service.spot.infra.chronicle.WalField.amount).int64();
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

    protected abstract void onSenderStop();
}
