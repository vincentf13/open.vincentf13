package open.vincentf13.service.spot_exchange.gateway;

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
  Gateway 訊息轉發器
  負責輪詢 GW_Q 並透過 Aeron (Stream 10) 傳送到 Core
 */
@Component
public class GatewayAeronSender implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(GatewayAeronSender.class);
    private final Aeron aeron;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ChronicleQueue gwQueue;
    private Publication publication;
    private ExecutorService executor;

    public GatewayAeronSender(Aeron aeron) {
        this.aeron = aeron;
    }

    @PostConstruct
    public void start() {
        gwQueue = SingleChronicleQueueBuilder.binary("data/spot_exchange/gw-queue").build();
        publication = aeron.addPublication("aeron:udp?endpoint=localhost:40444", 10);
        
        running.set(true);
        executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "gw-aeron-sender"));
        executor.submit(this);
    }

    @Override
    public void run() {
        ExcerptTailer tailer = gwQueue.createTailer();
        UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));

        while (running.get()) {
            boolean handled = tailer.readDocument(wire -> {
                // 將 Wire 數據封裝到 Aeron Buffer 並發送
                // 注意：在正式環境中應使用 SBE 編碼
                byte[] data = wire.bytes().toByteArray();
                buffer.putBytes(0, data);
                
                long result = publication.offer(buffer, 0, data.length);
                if (result < 0) {
                    log.warn("Aeron 發送失敗: {}", result);
                    // 這裡應有重試機制 (Backpressure)
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
        if (gwQueue != null) gwQueue.close();
    }
}
