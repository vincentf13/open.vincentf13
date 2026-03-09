package open.vincentf13.service.spot_exchange.core;

import io.aeron.Aeron;
import io.aeron.Publication;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** 
  Core 結果發送器 (Stream 30)
  負責輪詢 outbound-queue 並透過 Aeron 將執行報告發送給 Gateway
 */
@Component
public class CoreResultSender implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(CoreResultSender.class);
    private final Aeron aeron;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ChronicleQueue outboundQueue;
    private Publication publication;
    private ExecutorService executor;

    public CoreResultSender(Aeron aeron) {
        this.aeron = aeron;
    }

    @PostConstruct
    public void start() {
        outboundQueue = SingleChronicleQueueBuilder.binary("data/spot_exchange/outbound-queue").build();
        publication = aeron.addPublication("aeron:udp?endpoint=localhost:40445", 30);
        
        running.set(true);
        executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "core-result-sender"));
        executor.submit(this);
    }

    @Override
    public void run() {
        ExcerptTailer tailer = outboundQueue.createTailer();
        UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));

        while (running.get()) {
            boolean handled = tailer.readDocument(wire -> {
                // 將執行報告序列化並發送 (SBE 模式模擬)
                byte[] data = wire.bytes().toByteArray();
                buffer.putBytes(0, data);
                long result = publication.offer(buffer, 0, data.length);
                if (result < 0 && result != -2) { // -2 為 Backpressured，MVP 暫不處理
                    log.warn("Result Aeron 發送失敗: {}", result);
                }
            });

            if (!handled) {
                Thread.onSpinWait();
            }
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (executor != null) executor.shutdown();
        if (outboundQueue != null) outboundQueue.close();
    }
}
