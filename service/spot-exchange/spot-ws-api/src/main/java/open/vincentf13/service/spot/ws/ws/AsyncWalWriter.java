package open.vincentf13.service.spot.ws.ws;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.wire.DocumentContext;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import org.agrona.concurrent.ringbuffer.ManyToOneRingBuffer;
import org.springframework.stereotype.Component;

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
        Storage.self().metricsHistory().put(Storage.KEY_CPU_ID_WAL_WRITER, (long) cpuId);
    }

    @Override
    protected void onStart() {
        this.appender = wal.acquireAppender();
        log.info("Async WAL Writer started, ready to consume from RingBuffer");
    }

    @Override
    protected int doWork() {
        // 批次寫入優化：開啟一個 DocumentContext 寫入多條訊息
        try (DocumentContext dc = appender.writingDocument()) {
            net.openhft.chronicle.bytes.Bytes<?> bytes = dc.wire().bytes();
            final net.openhft.chronicle.bytes.PointerBytesStore pointer = open.vincentf13.service.spot.infra.alloc.ThreadContext.get().getReusablePointer();
            
            int workDone = queue.read((msgTypeId, buffer, offset, length) -> {
                // 寫入長度前綴與數據 (零拷貝)
                bytes.writeInt(length);
                pointer.set(buffer.addressOffset() + offset, length);
                bytes.write(pointer);

                localWalWriteCount++;
                if (localWalWriteCount >= METRICS_BATCH_SIZE) {
                    final long batch = localWalWriteCount;
                    Storage.self().metricsHistory().compute(Storage.KEY_GATEWAY_WAL_WRITE_COUNT, (k, v) -> v == null ? batch : v + batch);
                    localWalWriteCount = 0;
                }
                log.debug("[ASYNC-WAL] 訊息已加入批次, len={}", length);
            }, 100);

            if (workDone == 0) {
                dc.rollbackOnClose();
            }
            return workDone;
        }
    }

    @Override
    protected void onStop() {
        log.info("Async WAL Writer stopped");
    }
}
