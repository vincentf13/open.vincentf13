package open.vincentf13.service.spot.matching.aeron;

import io.aeron.Aeron;
import jakarta.annotation.PostConstruct;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot.infra.aeron.Publisher;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Progress;

import java.nio.ByteBuffer;

import static open.vincentf13.service.spot.infra.Constants.*;

@Component
public class AeronSender extends Worker {
    private final Aeron aeron;
    private final Storage storage;
    private Publisher publisher;
    private ExcerptTailer tailer;
    private final Progress progress = new Progress();
    
    private final UnsafeBuffer aeronBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(1024);

    public AeronSender(Aeron aeron, Storage storage) {
        this.aeron = aeron;
        this.storage = storage;
    }

    @PostConstruct public void init() { start("core-result-sender"); }

    @Override
    protected void onStart() {
        publisher = new Publisher(aeron, OUTBOUND_CHANNEL, OUTBOUND_STREAM_ID);
        tailer = storage.resultQueue().createTailer();
        Progress saved = storage.metadata().get(PK_CORE_OUTBOUND_SEQ);
        if (saved != null) {
            progress.setLastProcessedSeq(saved.getLastProcessedSeq());
            tailer.moveToIndex(progress.getLastProcessedSeq());
        }
    }

    @Override
    protected int doWork() {
        boolean handled = tailer.readDocument(wire -> {
            long seq = tailer.index();
            reusableBytes.clear();
            wire.read("payload").bytes(reusableBytes);
            aeronBuffer.wrap(reusableBytes.addressForRead(reusableBytes.readPosition()), (int)reusableBytes.readRemaining());
            
            while (publisher.tryPublish(aeronBuffer, 0, aeronBuffer.capacity()) < 0) {
                if (!running.get()) return;
                idleStrategy.idle();
            }
            progress.setLastProcessedSeq(seq);
            storage.metadata().put(PK_CORE_OUTBOUND_SEQ, progress);
        });
        return handled ? 1 : 0;
    }

    @Override
    protected void onStop() { 
        if (publisher != null) publisher.close();
        reusableBytes.releaseLast(); 
    }
}
