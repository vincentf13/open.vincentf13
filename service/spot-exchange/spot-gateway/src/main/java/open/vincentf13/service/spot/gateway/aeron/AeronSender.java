package open.vincentf13.service.spot.gateway.aeron;

import io.aeron.Aeron;
import jakarta.annotation.PostConstruct;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot.infra.aeron.Publisher;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.SystemProgress;

import java.nio.ByteBuffer;

import static open.vincentf13.service.spot.infra.Constants.*;

@Component
public class AeronSender extends Worker {
    private final Aeron aeron;
    private final Storage storage;
    private Publisher publisher;
    private ExcerptTailer tailer;
    private final SystemProgress progress = new SystemProgress();
    private final UnsafeBuffer aeronBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(1024);

    public AeronSender(Aeron aeron, Storage storage) {
        this.aeron = aeron; this.storage = storage;
    }

    @PostConstruct public void init() { start("aeron-sender"); }

    @Override
    protected void onStart() {
        publisher = new Publisher(aeron, INBOUND_CHANNEL, INBOUND_STREAM_ID);
        tailer = storage.gatewayQueue().createTailer();
        SystemProgress saved = storage.metadata().get(PK_GW_INBOUND_SEQ);
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
            storage.metadata().put(PK_GW_INBOUND_SEQ, progress);
        });
        return handled ? 1 : 0;
    }

    @Override
    protected void onStop() { 
        if (publisher != null) publisher.close();
        reusableBytes.releaseLast(); 
    }
}
