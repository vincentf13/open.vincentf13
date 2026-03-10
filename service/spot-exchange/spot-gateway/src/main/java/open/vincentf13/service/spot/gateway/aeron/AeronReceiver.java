package open.vincentf13.service.spot.gateway.aeron;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.SystemProgress;

import static open.vincentf13.service.spot.infra.Constants.*;

@Component
public class AeronReceiver extends Worker {
    private final Aeron aeron;
    private final Storage storage;
    private Subscription subscription;
    private FragmentHandler fragmentHandler;
    private final SystemProgress progress = new SystemProgress();
    
    private final byte[] reusableArray = new byte[2048];
    private final net.openhft.chronicle.bytes.Bytes<?> writeBytes = net.openhft.chronicle.bytes.Bytes.wrapForRead(reusableArray);

    public AeronReceiver(Aeron aeron, Storage storage) {
        this.aeron = aeron; this.storage = storage;
    }

    @PostConstruct public void init() { start("aeron-receiver"); }

    @Override
    protected void onStart() {
        subscription = aeron.addSubscription(OUTBOUND_CHANNEL, OUTBOUND_STREAM_ID);
        SystemProgress saved = storage.metadata().get(PK_GW_OUTBOUND_SEQ);
        if (saved != null) progress.setLastProcessedSeq(saved.getLastProcessedSeq());
        else progress.setLastProcessedSeq(-1L);

        fragmentHandler = (buffer, offset, length, header) -> {
            long currentSeq = header.position();
            if (currentSeq <= progress.getLastProcessedSeq()) return;

            int len = Math.min(length, reusableArray.length);
            buffer.getBytes(offset, reusableArray, 0, len);
            writeBytes.readPositionRemaining(0, len);
            
            storage.resultQueue().acquireAppender().writeDocument(wire -> {
                wire.bytes().write(writeBytes);
                wire.write("aeronSeq").int64(currentSeq);
            });
            
            progress.setLastProcessedSeq(currentSeq);
            storage.metadata().put(PK_GW_OUTBOUND_SEQ, progress);
        };
    }

    @Override protected int doWork() { return subscription.poll(fragmentHandler, 10); }

    @Override protected void onStop() { if (subscription != null) subscription.close(); }
}
