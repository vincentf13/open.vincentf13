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
 * 網關異步 WAL 寫入器 (Async WAL Writer)
 * 職責：從 RingBuffer 讀取數據，並獨佔式寫入 Chronicle Queue
 * 通過 BusySpinIdleStrategy 確保最低延遲與最高核心利用率
 */
@Slf4j
@Component
public class AsyncWalWriter extends Worker {
    private final ChronicleQueue wal = Storage.self().gatewaySenderWal();
    private final ManyToOneRingBuffer queue = Storage.self().gatewayWalQueue();
    private net.openhft.chronicle.queue.ExcerptAppender appender;
    private long localWalWriteCount = 0;
    private static final int METRICS_BATCH_SIZE = 10000;

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
        log.info("Async WAL Writer started, ready to consume from RingBuffer");
    }

    @Override
    protected int doWork() {
        // 批量從 RingBuffer 讀取，但每一筆訊息都開啟獨立的 WAL Document 以獲得唯一索引
        final net.openhft.chronicle.bytes.PointerBytesStore pointer = open.vincentf13.service.spot.infra.alloc.ThreadContext.get().getReusablePointer();
        
        return queue.read((msgTypeId, buffer, offset, length) -> {
            try (DocumentContext dc = appender.writingDocument()) {
                pointer.set(buffer.addressOffset() + offset, length);
                // 直接寫入數據，每一筆訊息都會獲得一個唯一的 dc.index()
                dc.wire().bytes().write(pointer);

                localWalWriteCount++;
                if (localWalWriteCount >= METRICS_BATCH_SIZE) {
                    MetricsCollector.add(MetricsKey.GATEWAY_WAL_WRITE_COUNT, localWalWriteCount);
                    localWalWriteCount = 0;
                }
                log.debug("[ASYNC-WAL] 訊息已持久化, index={}", dc.index());
            }
        }, 100);
    }

    @Override
    protected void onStop() {
        log.info("Async WAL Writer stopped");
    }
}
