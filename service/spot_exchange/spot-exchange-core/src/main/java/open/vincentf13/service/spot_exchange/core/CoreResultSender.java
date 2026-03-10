package open.vincentf13.service.spot_exchange.core;

import io.aeron.Aeron;
import io.aeron.Publication;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot_exchange.infra.AeronPublisher;
import open.vincentf13.service.spot_exchange.infra.BusySpinWorker;
import open.vincentf13.service.spot_exchange.infra.StateStore;

import java.nio.ByteBuffer;

@Component
public class CoreResultSender extends BusySpinWorker {
    private final Aeron aeron;
    private final StateStore stateStore;
    private AeronPublisher publisher;
    private ExcerptTailer tailer;
    
    private final UnsafeBuffer aeronBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(1024);

    public CoreResultSender(Aeron aeron, StateStore stateStore) {
        this.aeron = aeron;
        this.stateStore = stateStore;
    }

    @PostConstruct
    public void init() { start("core-result-sender"); }

    @Override
    protected void onStart() {
        publisher = new AeronPublisher(aeron, "aeron:udp?endpoint=localhost:40445", 30);
        tailer = stateStore.getOutboundQueue().createTailer();
    }

    @Override
    protected int doWork() {
        boolean handled = tailer.readDocument(wire -> {
            reusableBytes.clear();
            wire.read("payload").bytes(reusableBytes);
            
            aeronBuffer.wrap(reusableBytes.addressForRead(reusableBytes.readPosition()), 
                             (int)reusableBytes.readRemaining());
            
            while (publisher.tryPublish(aeronBuffer, 0, aeronBuffer.capacity()) < 0) {
                if (!running.get()) return;
                idleStrategy.idle();
            }
        });
        return handled ? 1 : 0;
    }

    @Override
    protected void onStop() {
        if (publisher != null) publisher.close();
        if (reusableBytes != null) reusableBytes.releaseLast();
    }
}
