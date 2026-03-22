package open.vincentf13.service.spot.ws.ws;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.bytes.PointerBytesStore;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.wire.DocumentContext;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.infra.metrics.MetricsCollector;
import org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer;
import org.agrona.concurrent.MessageHandler;
import org.springframework.stereotype.Component;

import static open.vincentf13.service.spot.infra.Constants.*;

/**
 * 網關異步 WAL 寫入器 (Async WAL Writer) - 批次讀取 / Raw 寫入版
 */
@Slf4j
@Component
public class AsyncWalWriter extends Worker {
    private final ChronicleQueue wal = Storage.self().gatewaySenderWal();
    private final ManyToOneRingBuffer queue = Storage.self().gatewayWalQueue();
    private net.openhft.chronicle.queue.ExcerptAppender appender;
    
    // 放寬批次上限
    private static final int BATCH_SIZE = 10000;
    private final PointerBytesStore pointer = new PointerBytesStore();

    // 性能優化：單筆 Document Raw 寫入，確保每筆訊息擁有獨立 Index
    private final MessageHandler handler = (msgTypeId, buffer, offset, length) -> {
        appender.writeBytes(b -> {
            pointer.set(buffer.addressOffset() + offset, length);
            // 使用 Raw 寫入，避開 Wire 協議層
            b.write(pointer);
        });
    };

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
        log.info("Async WAL Writer (Batch-Read / Raw-Write) started");
    }

    @Override
    protected int doWork() {
        // 批次從 RingBuffer 讀取並寫入 WAL
        // 由於 Storage 配置為 ASYNC，寫入將由 OS 背景刷新，不阻塞執行緒
        int count = queue.read(handler, BATCH_SIZE);
        if (count > 0) {
            MetricsCollector.add(MetricsKey.GATEWAY_WAL_WRITE_COUNT, count);
        }
        return count;
    }

    @Override
    protected void onStop() {
        log.info("Async WAL Writer stopped");
    }
}
