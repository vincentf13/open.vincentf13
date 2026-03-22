package open.vincentf13.service.spot.ws.ws;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
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
 * 網關異步 WAL 寫入器 (Async WAL Writer) - 批次讀取/異步刷盤版
 */
@Slf4j
@Component
public class AsyncWalWriter extends Worker {
    private final ChronicleQueue wal = Storage.self().gatewaySenderWal();
    private final ManyToOneRingBuffer queue = Storage.self().gatewayWalQueue();
    private net.openhft.chronicle.queue.ExcerptAppender appender;
    
    // 批次處理上限
    private static final int BATCH_SIZE = 1024;
    private open.vincentf13.service.spot.infra.alloc.ThreadContext threadContext;

    // 性能優化：Handler 內部執行單筆 Document 寫入，確保每筆訊息擁有獨立 Index
    private final MessageHandler handler = (msgTypeId, buffer, offset, length) -> {
        try (DocumentContext dc = appender.writingDocument()) {
            final var pointer = threadContext.getReusablePointer();
            pointer.set(buffer.addressOffset() + offset, length);
            dc.wire().bytes().write(pointer);
            MetricsCollector.increment(MetricsKey.GATEWAY_WAL_WRITE_COUNT);
        }
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
        this.threadContext = open.vincentf13.service.spot.infra.alloc.ThreadContext.get();
        log.info("Async WAL Writer (Batch-Read / Async-Flush) started");
    }

    @Override
    protected int doWork() {
        // 批次從 RingBuffer 讀取並寫入 WAL
        // 由於 Storage 配置為 ASYNC，dc.close() 不會觸發磁碟 IO 阻塞
        return queue.read(handler, BATCH_SIZE);
    }

    @Override
    protected void onStop() {
        log.info("Async WAL Writer stopped");
    }
}
