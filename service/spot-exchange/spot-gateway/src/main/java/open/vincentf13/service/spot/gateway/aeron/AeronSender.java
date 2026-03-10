package open.vincentf13.service.spot.gateway.aeron;

import io.aeron.Aeron;
import io.aeron.Publication;
import jakarta.annotation.PostConstruct;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.queue.ExcerptTailer;
import org.agrona.concurrent.UnsafeBuffer;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import open.vincentf13.service.spot.model.Progress;

import java.nio.ByteBuffer;

import static open.vincentf13.service.spot.infra.Constants.*;

@Component
public class AeronSender extends Worker {
    private final Aeron aeron;
    private Publication publication;
    private ExcerptTailer tailer;
    private final Progress progress = new Progress();
    private final UnsafeBuffer aeronBuffer = new UnsafeBuffer(0, 0);
    private final Bytes<ByteBuffer> reusableBytes = Bytes.elasticByteBuffer(1024);

    public AeronSender(Aeron aeron) {
        this.aeron = aeron;
    }

    @PostConstruct public void init() { start("gw-aeron-sender"); }

    @Override
    protected void onStart() {
        publication = aeron.addPublication(Channel.INBOUND, Channel.IN_STREAM);
        tailer = Storage.self().gatewayQueue().createTailer();
        Progress saved = Storage.self().metadata().get(Pk.GATEWAY_IN);
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
            
            // 實作背壓處理與重試邏輯
            while (publication.offer(aeronBuffer, 0, aeronBuffer.capacity()) < 0) {
                if (!running.get()) return;
                idleStrategy.idle();
            }
            progress.setLastProcessedSeq(seq);
            Storage.self().metadata().put(Pk.GATEWAY_IN, progress);
        });
        return handled ? 1 : 0;
    }

    @Override
    protected void onStop() { 
        if (publication != null) publication.close();
        reusableBytes.releaseLast(); 
    }
}
