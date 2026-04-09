package open.vincentf13.service.spot.ws.aeron;

import com.lmax.disruptor.EventPoller;
import com.lmax.disruptor.RingBuffer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.wire.Wire;
import open.vincentf13.service.spot.infra.aeron.AeronConstants;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.chronicle.WalField;
import open.vincentf13.service.spot.infra.metrics.StaticMetricsHolder;
import open.vincentf13.service.spot.infra.util.PreTouchUtil;
import open.vincentf13.service.spot.ws.wal.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

import static open.vincentf13.service.spot.infra.Constants.*;

/**
 * WAL 持久化 + Aeron 發送 (生產模式)
 *
 * Netty → Disruptor → WalSender (寫 WAL + 送 Aeron) → Matching Engine
 * 寫入順序保證：先寫 WAL 拿到 walIndex，再送 Aeron。
 * RESUME 握手後從 WAL replay 追趕，再切換到 live 模式。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "spot.wal.bypass", havingValue = "false", matchIfMissing = true)
public class WalSender extends GatewaySender {

    private final ChronicleQueue wal;
    private ExcerptAppender appender;
    private ExcerptTailer replayTailer;
    private long resumeSkipIndex = Long.MIN_VALUE;
    private boolean replaying = false;
    private Thread preTouchDaemon;

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
        PreTouchUtil.touchDirectory(new File(ChronicleMapEnum.WAL_BASE_DIR));
        this.appender = wal.acquireAppender();
        this.replayTailer = wal.createTailer();
        startPreTouchDaemon();
    }

    /**
     * 背景 pretouch daemon：每 50ms 掃描 WAL 目錄，對新增頁面執行預讀取，
     * 將 page fault 成本從 WalSender 熱路徑轉移到背景冷路徑。
     */
    private void startPreTouchDaemon() {
        File walDir = new File(ChronicleMapEnum.WAL_BASE_DIR
                + ChronicleQueueEnum.CLIENT_TO_GW.getPath());
        preTouchDaemon = Thread.ofPlatform().daemon().name("wal-pretouch").start(() -> {
            long lastTouchedSize = 0;
            String lastFileName = null;
            while (running.get()) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                File[] files = walDir.listFiles((d, name) -> name.endsWith(".cq4"));
                if (files == null || files.length == 0) continue;
                File latest = files[files.length - 1];
                String name = latest.getName();
                long size = latest.length();
                // 檔案切換時重置追蹤
                if (!name.equals(lastFileName)) { lastTouchedSize = 0; lastFileName = name; }
                if (size <= lastTouchedSize) continue;
                // 只觸摸尚未 fault 的新頁面
                try (FileChannel ch = FileChannel.open(latest.toPath(), StandardOpenOption.READ)) {
                    long from = lastTouchedSize;
                    long chunkSize = size - from;
                    MappedByteBuffer buf = ch.map(FileChannel.MapMode.READ_ONLY, from, chunkSize);
                    for (int i = 0; i < (int) chunkSize; i += 4096) { buf.get(i); }
                } catch (IOException ignored) {}
                lastTouchedSize = size;
            }
        });
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

    // ===== WAL 寫入 =====

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
        if (preTouchDaemon != null) preTouchDaemon.interrupt();
    }
}
