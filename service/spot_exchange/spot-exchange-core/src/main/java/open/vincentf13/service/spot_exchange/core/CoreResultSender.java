package open.vincentf13.service.spot_exchange.core;

import io.aeron.Aeron;
import jakarta.annotation.PostConstruct;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot_exchange.infra.*;
import open.vincentf13.service.spot_exchange.model.SystemProgress;

import java.nio.ByteBuffer;

import static open.vincentf13.service.spot_exchange.infra.ExchangeConstants.*;

@Component
public class CoreResultSender extends BusySpinWorker {
    private final Aeron aeron;
    private final StateStore stateStore;
    private AeronPublisher publisher;
    private ExcerptTailer tailer;
    private final SystemProgress progress = new SystemProgress();
    
    private final UnsafeBuffer aeronBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(1024);

    public CoreResultSender(Aeron aeron, StateStore stateStore) {
        this.aeron = aeron;
        this.stateStore = stateStore;
    }

    @PostConstruct public void init() { start("core-result-sender"); }

    @Override
    protected void onStart() {
        publisher = new AeronPublisher(aeron, OUTBOUND_CHANNEL, OUTBOUND_STREAM_ID);
        tailer = stateStore.getOutboundQueue().createTailer();
        SystemProgress saved = stateStore.getSystemMetadataMap().get(PK_CORE_OUTBOUND_SEQ);
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
            stateStore.getSystemMetadataMap().put(PK_CORE_OUTBOUND_SEQ, progress);
        });
        return handled ? 1 : 0;
    }

    @Override
    protected void onStop() { 
        if (publisher != null) publisher.close();
        reusableBytes.releaseLast(); 
    }
}
