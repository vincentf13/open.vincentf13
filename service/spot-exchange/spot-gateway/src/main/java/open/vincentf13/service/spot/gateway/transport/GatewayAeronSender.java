package open.vincentf13.service.spot.gateway.transport;

import io.aeron.Aeron;
import jakarta.annotation.PostConstruct;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot.infra.transport.AeronPublisher;
import open.vincentf13.service.spot.infra.worker.BusySpinWorker;
import open.vincentf13.service.spot.infra.store.StateStore;
import open.vincentf13.service.spot.model.SystemProgress;

import java.nio.ByteBuffer;

import static open.vincentf13.service.spot.infra.constant.ExchangeConstants.*;

@Component
public class GatewayAeronSender extends BusySpinWorker {
    private final Aeron aeron;
    private final StateStore stateStore;
    private AeronPublisher publisher;
    private ExcerptTailer tailer;
    private final SystemProgress progress = new SystemProgress();
    private final UnsafeBuffer aeronBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(1024);

    public GatewayAeronSender(Aeron aeron, StateStore stateStore) {
        this.aeron = aeron; this.stateStore = stateStore;
    }

    @PostConstruct public void init() { start("gw-aeron-sender"); }

    @Override
    protected void onStart() {
        publisher = new AeronPublisher(aeron, INBOUND_CHANNEL, INBOUND_STREAM_ID);
        tailer = stateStore.getGwQueue().createTailer();
        SystemProgress saved = stateStore.getSystemMetadataMap().get(PK_GW_INBOUND_SEQ);
        if (saved != null) {
            progress.setLastProcessedSeq(saved.getLastProcessedSeq());
            tailer.moveToIndex(progress.getLastProcessedSeq());
        }
    }

    @Override
    protected int doWork() {
        boolean handled = tailer.readDocument(wire -> {
            long seq = tailer.index();
            reusableBytes.clear(); wire.read("payload").bytes(reusableBytes);
            aeronBuffer.wrap(reusableBytes.addressForRead(reusableBytes.readPosition()), (int)reusableBytes.readRemaining());
            while (publisher.tryPublish(aeronBuffer, 0, aeronBuffer.capacity()) < 0) {
                if (!running.get()) return;
                idleStrategy.idle();
            }
            progress.setLastProcessedSeq(seq);
            stateStore.getSystemMetadataMap().put(PK_GW_INBOUND_SEQ, progress);
        });
        return handled ? 1 : 0;
    }

    @Override
    protected void onStop() { 
        if (publisher != null) publisher.close();
        reusableBytes.releaseLast(); 
    }
}
