package open.vincentf13.service.spot_exchange.gateway;

import io.aeron.Aeron;
import io.aeron.Publication;
import jakarta.annotation.PostConstruct;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot_exchange.infra.AeronPublisher;
import open.vincentf13.service.spot_exchange.infra.BusySpinWorker;
import open.vincentf13.service.spot_exchange.infra.StateStore;

import java.nio.ByteBuffer;

@Component
public class GatewayAeronSender extends BusySpinWorker {
    private final Aeron aeron;
    private final StateStore stateStore;
    private AeronPublisher publisher;
    private ExcerptTailer tailer;
    
    // --- 深度優化：使用預分配 Bytes 與 UnsafeBuffer 實現 Zero-GC 轉發 ---
    private final UnsafeBuffer aeronBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(1024);

    public GatewayAeronSender(Aeron aeron, StateStore stateStore) {
        this.aeron = aeron;
        this.stateStore = stateStore;
    }

    @PostConstruct
    public void init() { start("gw-aeron-sender"); }

    @Override
    protected void onStart() {
        publisher = new AeronPublisher(aeron, "aeron:udp?endpoint=localhost:40444", 10);
        tailer = stateStore.getGwQueue().createTailer();
    }

    @Override
    protected int doWork() {
        boolean handled = tailer.readDocument(wire -> {
            // --- Zero-GC 讀取與發送 ---
            reusableBytes.clear();
            wire.read("payload").bytes(reusableBytes);
            
            aeronBuffer.wrap(reusableBytes.addressForRead(reusableBytes.readPosition()), 
                             (int)reusableBytes.readRemaining());
            
            long result;
            while ((result = publisher.tryPublish(aeronBuffer, 0, aeronBuffer.capacity())) < 0) {
                if (!running.get()) return;
                idleStrategy.idle();
            }
        });
        return handled ? 1 : 0;
    }

    @Override
    protected void onStop() {
        if (publisher != null) publisher.close();
        if (reusableBytes != null) reusableBytes.release();
    }
}
