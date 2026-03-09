package open.vincentf13.service.spot_exchange.gateway;

import io.aeron.Aeron;
import io.aeron.Publication;
import jakarta.annotation.PostConstruct;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot_exchange.infra.AeronPublisher;
import open.vincentf13.service.spot_exchange.infra.BusySpinWorker;

import java.nio.ByteBuffer;

@Component
public class GatewayAeronSender extends BusySpinWorker {
    private final Aeron aeron;
    private ChronicleQueue gwQueue;
    private AeronPublisher publisher;
    private ExcerptTailer tailer;
    private final UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(1024));

    public GatewayAeronSender(Aeron aeron) {
        this.aeron = aeron;
    }

    @PostConstruct
    public void init() {
        start("gw-aeron-sender");
    }

    @Override
    protected void onStart() {
        gwQueue = SingleChronicleQueueBuilder.binary("data/spot_exchange/gw-queue").build();
        publisher = new AeronPublisher(aeron, "aeron:udp?endpoint=localhost:40444", 10);
        tailer = gwQueue.createTailer();
    }

    @Override
    protected int doWork() {
        boolean handled = tailer.readDocument(wire -> {
            byte[] data = wire.bytes().toByteArray();
            buffer.putBytes(0, data);
            
            // --- 深度加固：阻塞重試直至發送成功 ---
            long result;
            while ((result = publisher.tryPublish(buffer, 0, data.length)) < 0) {
                if (result == Publication.NOT_CONNECTED) {
                    log.warn("Aeron Publisher 未連接，重試中...");
                }
                idleStrategy.idle(); // 忙等或微休眠
                if (!running.get()) return;
            }
        });
        return handled ? 1 : 0;
    }

    @Override
    protected void onStop() {
        if (publisher != null) publisher.close();
        if (gwQueue != null) gwQueue.close();
    }
}
