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
import open.vincentf13.service.spot.infra.util.PreTouchUtil;
import open.vincentf13.service.spot.ws.wal.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.RandomAccessFile;
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
     * 背景 pretouch daemon：透過 RandomAccessFile.read() 將 WAL 檔案的新頁面
     * 預讀入 OS page cache，讓 Chronicle Queue 的 mmap 訪問時不觸發 page fault。
     *
     * 不使用 FileChannel.map（Windows 會與 CQ 的 mmap 搶文件鎖），
     * 也不使用 CQ Enterprise 的 pretouch()（開源版無效）。
     * RandomAccessFile.read() 走標準 I/O 路徑，與 mmap 共享同一個 page cache，
     * 且不需要任何文件鎖。
     */
    private void startPreTouchDaemon() {
        File walDir = new File(ChronicleMapEnum.WAL_BASE_DIR);
        preTouchDaemon = Thread.ofPlatform().daemon().name("wal-pretouch").start(() -> {
            long lastTouchedSize = 0;
            byte[] buf = new byte[1];
            while (running.get()) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                try {
                    lastTouchedSize = pretouchNewPages(walDir, lastTouchedSize, buf);
                } catch (Exception ignored) {}
            }
        });
    }

    /**
     * 掃描 WAL 目錄，對自上次以來新增的檔案區域逐頁讀取，
     * 觸發 OS 將對應的磁碟頁面載入 page cache。
     *
     * @return 本次掃描後的最大檔案大小，供下次增量比較
     */
    private static long pretouchNewPages(File dir, long lastTouchedSize, byte[] buf) {
        long maxSize = lastTouchedSize;
        File[] subDirs = dir.listFiles(File::isDirectory);
        if (subDirs == null) return maxSize;
        for (File sub : subDirs) {
            File[] cq4Files = sub.listFiles((d, n) -> n.endsWith(".cq4"));
            if (cq4Files == null) continue;
            for (File f : cq4Files) {
                long len = f.length();
                if (len <= lastTouchedSize) continue;
                try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
                    // 只觸碰新增區域，避免重複讀取已快取的頁面
                    long start = (lastTouchedSize > 0) ? lastTouchedSize : 0;
                    for (long pos = start; pos < len; pos += 4096) {
                        raf.seek(pos);
                        raf.read(buf);
                    }
                } catch (Exception ignored) {}
                if (len > maxSize) maxSize = len;
            }
        }
        return maxSize;
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
        if (preTouchDaemon != null) preTouchDaemon.interrupt();
    }
}
