package open.vincentf13.service.spot.ws.aeron;

import com.lmax.disruptor.EventPoller;
import com.lmax.disruptor.RingBuffer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.Wire;
import open.vincentf13.service.spot.infra.aeron.AeronConstants;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import open.vincentf13.service.spot.ws.wal.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/**
 * WAL Aeron 發送器 (依賴 WalWriter 完成落盤)
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "spot.wal.bypass", havingValue = "false", matchIfMissing = true)
public class WalAeronSender extends GatewaySender {

    private final ChronicleQueue wal;
    private ExcerptTailer replayTailer;
    private long resumeSkipIndex = Long.MIN_VALUE;
    private boolean replaying = false;

    public WalAeronSender(@SuppressWarnings("unused") io.aeron.Aeron aeron, RingBuffer<WalEvent> ringBuffer, WalWriter walWriter) {
        super("wal-aeron-sender",
              MetricsKey.CPU_ID_AERON_SENDER, MetricsKey.CPU_ID_CURRENT_AERON_SENDER,
              MetricsKey.GATEWAY_AERON_SENDER_DUTY_CYCLE, ringBuffer, walWriter.getSequence());
        this.wal = Storage.self().gatewaySenderWal();
    }

    @PostConstruct @Override public void start() { super.start(); }
    @PreDestroy @Override public void stop() { super.stop(); }

    @Override
    protected final void onSenderStart() {
        this.replayTailer = wal.createTailer();
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
                    log.info("[AERON-SENDER] WAL replay 完成，切換到 live 模式");
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

    // ===== LIVE：Disruptor(已有 walIndex) → Aeron =====

    private final EventPoller.Handler<WalEvent> liveHandler = (event, sequence, endOfBatch) -> {
        long pollTimeNs = DIAGNOSE ? System.nanoTime() : 0;
        long walIndex = event.walIndex;
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
        try { poller.poll(liveHandler); } catch (Exception e) { log.error("[AERON-SENDER] live processing failed", e); }
        // 不在這裡累加 localWriteCount，避免干擾 GatewaySender 內建的上報，WAL 計數由 WalWriter 負責
        return pollCount;
    }

    @Override
    protected void onMetricsReport() {
        // 調用父類負責 AERON_BACKPRESSURE 的上報
        super.onMetricsReport();
    }

    // ===== RESUME =====

    @Override
    protected final void onResume(long walIndex) {
        log.info("[AERON-SENDER] RESUME 握手成功，恢復位點: {}", walIndex);
        if (walIndex == WAL_INDEX_NONE || walIndex == MSG_SEQ_NONE || !replayTailer.moveToIndex(walIndex)) {
            replayTailer.toStart();
            resumeSkipIndex = Long.MIN_VALUE;
        } else {
            resumeSkipIndex = walIndex;
        }
        replaying = true;
    }
}
