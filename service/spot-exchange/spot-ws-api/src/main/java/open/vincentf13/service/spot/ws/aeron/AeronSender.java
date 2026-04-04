package open.vincentf13.service.spot.ws.aeron;

import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.Wire;
import open.vincentf13.service.spot.infra.aeron.*;
import open.vincentf13.service.spot.infra.aeron.AeronConstants.AeronState;
import open.vincentf13.service.spot.infra.util.PreTouchUtil;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.chronicle.WalField;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import open.vincentf13.service.spot.infra.thread.Worker;
import open.vincentf13.service.spot.model.command.AbstractSbeModel;
import open.vincentf13.service.spot.sbe.*;
import org.agrona.MutableDirectBuffer;
import org.springframework.stereotype.Component;

import java.nio.ByteOrder;

import static open.vincentf13.service.spot.infra.Constants.*;
import static open.vincentf13.service.spot.infra.aeron.AeronUtil.*;

/**
 * 網關 Aeron 發送器
 *
 * 從 Chronicle Queue 讀取 BinaryWire 結構欄位，
 * 以 SBE 編碼組裝 Aeron 訊息 (32-byte header + SBE body)，
 * 直接寫入 Aeron claim buffer，達成單次拷貝。
 */
@Slf4j
@Component
public class AeronSender extends Worker {
    private final ChronicleQueue wal;
    private Publication publication;
    private Subscription controlSub;
    private ExcerptTailer tailer;
    private AeronState currentState = AeronState.WAITING;
    private long localBackPressure = 0;
    private long resumeSkipIndex = Long.MIN_VALUE;

    // SBE 編碼器 (單線程獨佔，無需 ThreadLocal)
    private final MessageHeaderEncoder sbeHeaderEncoder = new MessageHeaderEncoder();
    private final OrderCreateEncoder orderCreateEncoder = new OrderCreateEncoder();
    private final OrderCancelEncoder orderCancelEncoder = new OrderCancelEncoder();
    private final DepositEncoder depositEncoder = new DepositEncoder();
    private final AuthEncoder authEncoder = new AuthEncoder();

    public AeronSender() {
        super("gw-sender", MetricsKey.CPU_ID_AERON_SENDER, MetricsKey.CPU_ID_CURRENT_AERON_SENDER, MetricsKey.GATEWAY_AERON_SENDER_WORKER_DUTY_CYCLE);
        this.wal = Storage.self().gatewaySenderWal();
    }

    @PostConstruct @Override public void start() { super.start(); }
    @PreDestroy @Override public void stop() { super.stop(); }

    @Override
    protected void onStart() {
        PreTouchUtil.touchDirectory(new java.io.File(ChronicleMapEnum.WAL_BASE_DIR));
        this.publication = AeronUtil.aeron().addPublication(AeronChannel.MATCHING_FLOW, AeronChannel.DATA_STREAM_ID);
        this.controlSub = AeronUtil.aeron().addSubscription(AeronChannel.REPORT_FLOW, AeronChannel.CONTROL_STREAM_ID);
        this.tailer = wal.createTailer();
    }

    @Override
    protected int doWork() {
        if (currentState == AeronState.SENDING && !publication.isConnected()) currentState = AeronState.WAITING;

        int work = controlSub.poll(resumeHandler, AeronConstants.AERON_POLL_LIMIT);
        if (currentState == AeronState.WAITING) return work;

        for (int i = 0; i < AeronConstants.WAL_BATCH_SIZE; i++) {
            try (var dc = tailer.readingDocument()) {
                if (!dc.isPresent()) break;

                long walIndex = dc.index();
                if (walIndex == resumeSkipIndex) { resumeSkipIndex = Long.MIN_VALUE; continue; }

                Wire wire = dc.wire();
                if (wire == null) break;

                if (!readAndSend(wire, walIndex)) break;

                StaticMetricsHolder.addCounter(MetricsKey.AERON_SEND_COUNT, 1);
                work++;
            }
        }
        return work;
    }

    /**
     * 從 BinaryWire 讀取結構欄位，以 SBE 編碼直接寫入 Aeron claim buffer。
     *
     * Aeron 訊息格式 (與 Matching Engine 的 CommandRouter 對齊)：
     * [0-3] MsgType | [4-7] Pad | [8-15] Seq | [16-23] GwTime | [24-31] SBE Header | [32+] SBE Body
     */
    private boolean readAndSend(Wire wire, long walIndex) {
        int msgType   = wire.read(WalField.msgType).int32();
        long gwTime   = wire.read(WalField.gwTime).int64();
        long userId   = wire.read(WalField.userId).int64();
        long ts       = wire.read(WalField.timestamp).int64();

        return switch (msgType) {
            case MsgType.ORDER_CREATE -> {
                int symbolId   = wire.read(WalField.symbolId).int32();
                long price     = wire.read(WalField.price).int64();
                long qty       = wire.read(WalField.qty).int64();
                byte side      = wire.read(WalField.side).int8();
                long cid       = wire.read(WalField.clientOrderId).int64();
                int len = AbstractSbeModel.BODY_OFFSET + OrderCreateEncoder.BLOCK_LENGTH;
                yield trySendSbe(msgType, len, walIndex, gwTime, (buf, off) ->
                    orderCreateEncoder.wrapAndApplyHeader(buf, off + AbstractSbeModel.SBE_HEADER_OFFSET, sbeHeaderEncoder)
                        .timestamp(ts).userId(userId).symbolId(symbolId)
                        .price(price).qty(qty).side(Side.get((short) side)).clientOrderId(cid));
            }
            case MsgType.ORDER_CANCEL -> {
                long orderId = wire.read(WalField.orderId).int64();
                int len = AbstractSbeModel.BODY_OFFSET + OrderCancelEncoder.BLOCK_LENGTH;
                yield trySendSbe(msgType, len, walIndex, gwTime, (buf, off) ->
                    orderCancelEncoder.wrapAndApplyHeader(buf, off + AbstractSbeModel.SBE_HEADER_OFFSET, sbeHeaderEncoder)
                        .timestamp(ts).userId(userId).orderId(orderId));
            }
            case MsgType.DEPOSIT -> {
                int assetId  = wire.read(WalField.assetId).int32();
                long amount  = wire.read(WalField.amount).int64();
                int len = AbstractSbeModel.BODY_OFFSET + DepositEncoder.BLOCK_LENGTH;
                yield trySendSbe(msgType, len, walIndex, gwTime, (buf, off) ->
                    depositEncoder.wrapAndApplyHeader(buf, off + AbstractSbeModel.SBE_HEADER_OFFSET, sbeHeaderEncoder)
                        .timestamp(ts).userId(userId).assetId(assetId).amount(amount));
            }
            case MsgType.AUTH -> {
                int len = AbstractSbeModel.BODY_OFFSET + AuthEncoder.BLOCK_LENGTH;
                yield trySendSbe(msgType, len, walIndex, gwTime, (buf, off) ->
                    authEncoder.wrapAndApplyHeader(buf, off + AbstractSbeModel.SBE_HEADER_OFFSET, sbeHeaderEncoder)
                        .timestamp(ts).userId(userId));
            }
            default -> true; // 跳過未知類型
        };
    }

    /**
     * 組裝 32-byte header + SBE body 並透過 Aeron claim 模式發送。
     * bodyFiller 負責寫入 SBE header (offset 24) 與 body (offset 32+)。
     */
    private boolean trySendSbe(int msgType, int totalLen, long walIndex, long gwTime, AeronUtil.AeronHandler bodyFiller) {
        while (running.get()) {
            int res = AeronUtil.send(publication, totalLen, (buf, off) -> {
                buf.putInt(off + AbstractSbeModel.TYPE_OFFSET, msgType, ByteOrder.LITTLE_ENDIAN);
                buf.putInt(off + 4, 0, ByteOrder.LITTLE_ENDIAN); // padding
                buf.putLong(off + AbstractSbeModel.SEQ_OFFSET, walIndex, ByteOrder.LITTLE_ENDIAN);
                buf.putLong(off + AbstractSbeModel.GATEWAY_TIME_OFFSET, gwTime, ByteOrder.LITTLE_ENDIAN);
                bodyFiller.onFill(buf, off);
            });

            if (res == SEND_OK) {
                if (walIndex == 1) log.info("成功發送第一條消息 (seq=1)");
                return true;
            }
            if (res == SEND_BACKPRESSURE) { localBackPressure++; Thread.onSpinWait(); continue; }
            if (res == SEND_DISCONNECTED) {
                log.warn("AeronSender 鏈路斷開，暫停發送。");
                currentState = AeronState.WAITING;
            }
            return false;
        }
        return false;
    }

    private final FragmentHandler resumeHandler = (buffer, offset, length, header) -> {
        if (buffer.getInt(offset, ByteOrder.LITTLE_ENDIAN) == MsgType.RESUME && currentState == AeronState.WAITING) {
            long walIndex = buffer.getLong(offset + AeronConstants.MSG_SEQ_OFFSET, ByteOrder.LITTLE_ENDIAN);
            log.info("AeronSender 握手成功，恢復位點: {}", walIndex);
            if (walIndex == WAL_INDEX_NONE || walIndex == MSG_SEQ_NONE || !tailer.moveToIndex(walIndex)) {
                tailer.toStart();
                resumeSkipIndex = Long.MIN_VALUE;
            } else {
                resumeSkipIndex = walIndex;
            }
            currentState = AeronState.SENDING;
        }
    };

    @Override
    protected void onMetricsReport() {
        if (localBackPressure > 0) {
            StaticMetricsHolder.addCounter(MetricsKey.AERON_BACKPRESSURE, localBackPressure);
            localBackPressure = 0;
        }
    }

    @Override
    protected void onStop() {
        if (publication != null) publication.close();
        if (controlSub != null) controlSub.close();
    }
}
