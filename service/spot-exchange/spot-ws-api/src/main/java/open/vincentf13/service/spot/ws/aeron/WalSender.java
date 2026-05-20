package open.vincentf13.service.spot.ws.aeron;

import com.lmax.disruptor.EventPoller;
import com.lmax.disruptor.RingBuffer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.Wire;
import open.vincentf13.service.spot.infra.aeron.AeronConstants;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.chronicle.WalField;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import open.vincentf13.service.spot.ws.wal.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static open.vincentf13.service.spot.infra.Constants.*;

/**
 * WAL 持久化 + Aeron 發送 (生產模式)
 *
 * Netty → Disruptor → WalSender (寫 WAL + 送 Aeron) → Matching Engine
 * 寫入順序保證：先寫 WAL 拿到 walIndex，再送 Aeron。
 * RESUME 握手後從 WAL replay 追趕，再切換到 live 模式。
 */
@Slf4j
// 顯式同名 bean："gatewaySender" 在 bypass / WAL 模式下都存在，NettyServer @DependsOn 可不分模式引用
@Component("gatewaySender")
@ConditionalOnProperty(name = "spot.wal.bypass", havingValue = "false", matchIfMissing = true)
public class WalSender extends GatewaySender {

    private final ChronicleQueue wal;
    private ExcerptAppender appender;
    private ExcerptTailer replayTailer;
    private long resumeSkipIndex = Long.MIN_VALUE;
    private boolean replaying = false;

    public WalSender(@SuppressWarnings("unused") io.aeron.Aeron aeron, RingBuffer<WalEvent> ringBuffer) {
        super("wal-sender",
              MetricsKey.CPU_ID_WAL_SENDER, MetricsKey.CPU_ID_CURRENT_WAL_SENDER,
              MetricsKey.GATEWAY_WAL_SENDER_DUTY_CYCLE, ringBuffer);
        this.wal = Storage.self().gatewaySenderWal();
    }

    @PostConstruct @Override public void start() { super.start(); }
    @PreDestroy @Override public void stop() { super.stop(); }

    @Override
    protected final void onSenderStart() {
        this.appender = wal.acquireAppender();
        this.replayTailer = wal.createTailer();
    }

    // ===== WAITING：只寫 WAL (防 Disruptor 堆積) =====

    private final EventPoller.Handler<WalEvent> walOnlyHandler = (event, sequence, endOfBatch) -> {
        writeToWal(event);
        pollCount++;
        return true;
    };

    @Override
    protected final int drainWhileWaiting() {
        pollCount = 0;
        try { poller.poll(walOnlyHandler); } catch (Exception e) { log.error("[WAL-SENDER] WAL write failed", e); }
        if (pollCount > 0) localWriteCount += pollCount;
        return pollCount;
    }

    // ===== SENDING =====

    @Override
    protected final int processEvents() {
        if (replaying) {
            int work = replayFromWal();
            work += drainWhileWaiting();
            return work;
        }
        return processLive();
    }

    // ===== REPLAY：從 WAL 追趕 → Aeron =====

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

    // ===== LIVE：Disruptor → WAL → Aeron =====

    private final EventPoller.Handler<WalEvent> liveHandler = (event, sequence, endOfBatch) -> {
        long pollTimeNs = DIAGNOSE ? System.nanoTime() : 0;
        long walIndex = writeToWal(event);
        if (walIndex >= 0) {
            sendFromEvent(event, walIndex);
            StaticMetricsHolder.addCounter(MetricsKey.AERON_SEND_COUNT, 1);
            // 常駐量測 gateway 內部全程（Netty entry → Aeron commit done），含 WAL fsync
            StaticMetricsHolder.recordLatency(MetricsKey.LATENCY_GATEWAY_TOTAL, System.nanoTime() - event.arrivalTimeNs);
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

    // ===== WAL 寫入 (binary format, 跳過 Wire key-value 開銷) =====

    /** 預分配 write buffer，單線程獨佔，零分配 */
    private final byte[] walArr = new byte[128];
    private final ByteBuffer walBuf = ByteBuffer.wrap(walArr).order(ByteOrder.LITTLE_ENDIAN);

    private long writeToWal(WalEvent e) {
        walBuf.clear();
        walBuf.putInt(e.msgType);
        walBuf.putLong(e.arrivalTimeNs);
        walBuf.putLong(e.userId);
        walBuf.putLong(e.timestamp);
        switch (e.msgType) {
            case MsgType.ORDER_CREATE -> {
                WalOrderCreate oc = e.orderCreate;
                walBuf.putInt(oc.symbolId);
                walBuf.putLong(oc.price);
                walBuf.putLong(oc.qty);
                walBuf.put(oc.side);
                walBuf.putLong(oc.clientOrderId);
            }
            case MsgType.ORDER_CANCEL -> walBuf.putLong(e.orderCancel.orderId);
            case MsgType.DEPOSIT -> {
                WalDeposit d = e.deposit;
                walBuf.putInt(d.assetId);
                walBuf.putLong(d.amount);
            }
        }
        int len = walBuf.position();
        try (var dc = appender.writingDocument()) {
            dc.wire().bytes().write(walArr, 0, len);
            return dc.index();
        }
    }

    // ===== RESUME =====

    @Override
    protected final void onResume(long walIndex) {
        log.info("[WAL-SENDER] RESUME 握手成功，恢復位點: {}", walIndex);
        if (walIndex == WAL_INDEX_NONE || walIndex == MSG_SEQ_NONE || !replayTailer.moveToIndex(walIndex)) {
            replayTailer.toStart();
            resumeSkipIndex = Long.MIN_VALUE;
        } else {
            resumeSkipIndex = walIndex;
        }
        replaying = true;
    }

    @Override
    protected final void onSenderStop() {
        try { poller.poll(walOnlyHandler); } catch (Exception e) { log.warn("[WAL-SENDER] drain failed", e); }
    }
}
