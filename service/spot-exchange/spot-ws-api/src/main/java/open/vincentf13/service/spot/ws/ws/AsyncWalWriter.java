package open.vincentf13.service.spot.ws.ws;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.wire.DocumentContext;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.metrics.MetricsCollector;
import org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/**
 * 網關異步 WAL 寫入器 (Async WAL Writer) - 強一致性版
 * 職責：從 RingBuffer 讀取數據，並確保「每一筆」訊息都同步刷入磁碟。
 * 雖然性能會受限於磁碟 IOPS，但保證了數據的絕對零丟失。
 */
@Slf4j
@Component
public class AsyncWalWriter extends Worker {
    private final ChronicleQueue wal = Storage.self().gatewaySenderWal();
    private final ManyToOneRingBuffer queue = Storage.self().gatewayWalQueue();
    private net.openhft.chronicle.queue.ExcerptAppender appender;

    @PostConstruct
    public void init() {
        start("async-wal-writer");
    }

    @Override
    protected void onBind(int cpuId) {
        MetricsCollector.recordCpuAffinity(MetricsKey.CPU_ID_WAL_WRITER, cpuId);
    }

    @Override
    protected void onStart() {
        this.appender = wal.acquireAppender();
        log.info("Async WAL Writer (Strict Sync Mode) started - Consistency First");
    }

    @Override
    protected int doWork() {
        final var context = open.vincentf13.service.spot.infra.alloc.ThreadContext.get();
        final net.openhft.chronicle.bytes.PointerBytesStore pointer = context.getReusablePointer();
        
        // --- 核心變更：嚴格單筆處理 ---
        // 將讀取上限設為 1，確保 doWork 每次循環只處理並同步一筆訊息
        return queue.read((msgTypeId, buffer, offset, length) -> {
            // DocumentContext 在 close 時會觸發 ChronicleQueue 的同步寫入邏輯
            // 配合 Storage.java 中的 SyncMode.SYNC，這會執行磁碟 fsync
            try (DocumentContext dc = appender.writingDocument()) {
                pointer.set(buffer.addressOffset() + offset, length);
                dc.wire().bytes().write(pointer);
                
                // 這裡 dc.close() 會發生，並確保數據落盤
                MetricsCollector.increment(MetricsKey.GATEWAY_WAL_WRITE_COUNT);
            }
        }, 1); 
    }

    @Override
    protected void onStop() {
        log.info("Async WAL Writer stopped");
    }
}
