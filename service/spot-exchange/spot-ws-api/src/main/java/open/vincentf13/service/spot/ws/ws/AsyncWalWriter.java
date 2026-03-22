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
    
    // 性能優化：將 MessageHandler 定義為成員變數，避免 doWork 每次建立 Lambda 物件
    private final MessageHandler handler = (msgTypeId, buffer, offset, length) -> {
        // DocumentContext 是 Chronicle Queue 內部的重用物件，分配壓力較小
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
        log.info("Async WAL Writer (Ultra-Throughput) started");
    }

    @Override
    protected int doWork() {
        // 每次只讀 1 筆以保證強一致性，但使用預定義的 handler 消除分配
        return queue.read(handler, 1); 
    }

    @Override
    protected void onStop() {
        log.info("Async WAL Writer stopped");
    }
}
