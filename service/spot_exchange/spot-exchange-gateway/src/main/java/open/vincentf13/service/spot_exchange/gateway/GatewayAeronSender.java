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
import open.vincentf13.service.spot_exchange.infra.AeronPublisher;
import open.vincentf13.service.spot_exchange.infra.BusySpinWorker;

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
            publisher.publish(buffer, 0, data.length);
        });
        return handled ? 1 : 0;
    }

    @Override
    protected void onStop() {
        if (publisher != null) publisher.close();
        if (gwQueue != null) gwQueue.close();
    }
}
