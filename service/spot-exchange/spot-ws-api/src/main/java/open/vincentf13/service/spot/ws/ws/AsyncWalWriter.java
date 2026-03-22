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
 * 網關異步 WAL 寫入器 (Async WAL Writer) - 極致性能版
 */
@Slf4j
@Component
public class AsyncWalWriter extends Worker {
    private final ChronicleQueue wal = Storage.self().gatewaySenderWal();
    private final ManyToOneRingBuffer queue = Storage.self().gatewayWalQueue();
    private net.openhft.chronicle.queue.ExcerptAppender appender;
    
    // 批次處理參數
    private static final int BATCH_SIZE = 1024;
    
    // 性能優化：將 MessageHandler 定義為成員變數，避免 doWork 每次建立 Lambda 物件
    private final MessageHandler handler = (msgTypeId, buffer, offset, length) -> {
        // 使用 DocumentContext 進行高效寫入
        try (DocumentContext dc = appender.writingDocument()) {
            final var context = open.vincentf13.service.spot.infra.alloc.ThreadContext.get();
            final var pointer = context.getReusablePointer();
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
        log.info("Async WAL Writer (Group-Commit enabled) started");
    }

    @Override
    protected int doWork() {
        // 方案一：Group Commit (組提交)
        // 1. 嘗試從 RingBuffer 批量讀取最多 BATCH_SIZE 筆訊息
        int readCount = queue.read(handler, BATCH_SIZE);
        
        // 2. 如果有數據寫入，執行一次強制的磁碟同步 (攤提 fsync 開銷)
        if (readCount > 0) {
            // Chronicle Queue 底層通常是 MappedBytes，我們需要將其轉型以調用 force()
            // 這能確保這批次的所有數據都確實落盤
            final var bytes = appender.wire().bytes();
            if (bytes instanceof net.openhft.chronicle.bytes.MappedBytes mappedBytes) {
                mappedBytes.force();
            }
        }
        
        return readCount;
    }

    @Override
    protected void onStop() {
        log.info("Async WAL Writer stopped");
    }
}
