package open.vincentf13.service.spot.matching.aeron;

import io.aeron.Aeron;
import io.aeron.logbuffer.FragmentHandler;
import jakarta.annotation.PostConstruct;
import open.vincentf13.service.spot.infra.aeron.Subscriber;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import org.springframework.stereotype.Component;
import net.openhft.chronicle.bytes.PointerBytesStore;

import static open.vincentf13.service.spot.infra.Constants.INBOUND_CHANNEL;
import static open.vincentf13.service.spot.infra.Constants.INBOUND_STREAM_ID;

@Component
public class AeronReceiver extends Worker {
    private final Aeron aeron;
    private final Storage storage;
    private Subscriber subscriber;
    private FragmentHandler fragmentHandler;
    private final PointerBytesStore pointerBytesStore = new PointerBytesStore();

    public AeronReceiver(Aeron aeron, Storage storage) {
        this.aeron = aeron; this.storage = storage;
    }

    @PostConstruct public void init() { start("core-aeron-receiver"); }

    @Override
    protected void onStart() {
        subscriber = new Subscriber(aeron, INBOUND_CHANNEL, INBOUND_STREAM_ID);
        fragmentHandler = (buffer, offset, length, header) -> {
            pointerBytesStore.set(buffer.addressOffset() + offset, length);
            storage.commandQueue().acquireAppender().writeDocument(wire -> {
                wire.write("msgType").int32(100); 
                wire.write("payload").bytes(pointerBytesStore);
                wire.write("aeronSeq").int64(header.position()); 
            });
        };
    }

    @Override protected int doWork() { return subscriber.poll(fragmentHandler, 10); }

    @Override protected void onStop() { if (subscriber != null) subscriber.close(); }
}
