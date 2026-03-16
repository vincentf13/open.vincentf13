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
    private long localWalWriteCount = 0;
    private static final int METRICS_BATCH_SIZE = 10000;

    @PostConstruct
    public void init() {
        start("async-wal-writer");
    }

    @Override
    protected void onStart() {
        log.info("Async WAL Writer started, ready to consume from RingBuffer");
    }

    @Override
    protected int doWork() {
        // 批量讀取並處理 (Handler 會被回調處理每一條訊息)
        return queue.read(this::onMessage, 100);
    }

    private void onMessage(int msgTypeId, org.agrona.DirectBuffer buffer, int offset, int length) {
        try (DocumentContext dc = wal.acquireAppender().writingDocument()) {
            net.openhft.chronicle.bytes.Bytes<?> bytes = dc.wire().bytes();
            // 直接將數據從 RingBuffer 寫入 WAL (零拷貝)
            bytes.write(buffer, offset, length);

            localWalWriteCount++;
            if (localWalWriteCount >= METRICS_BATCH_SIZE) {
                final long batch = localWalWriteCount;
                Storage.self().metricsHistory().compute(Storage.KEY_GATEWAY_WAL_WRITE_COUNT, (k, v) -> v == null ? batch : v + batch);
                localWalWriteCount = 0;
            }
            log.debug("[ASYNC-WAL] 訊息已持久化, index={}", dc.index());
        }
    }

    @Override
    protected void onStop() {
        log.info("Async WAL Writer stopped");
    }
}
