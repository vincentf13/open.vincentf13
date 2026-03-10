package open.vincentf13.service.spot.matching.aeron;

import io.aeron.Aeron;
import io.aeron.logbuffer.FragmentHandler;
import jakarta.annotation.PostConstruct;
import open.vincentf13.service.spot.infra.aeron.Subscriber;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import org.springframework.stereotype.Component;
import net.openhft.chronicle.bytes.PointerBytesStore;

import static open.vincentf13.service.spot.infra.Constants.*;

@Component
public class AeronReceiver extends Worker {
    private final Aeron aeron;
    private Subscriber subscriber;
    private FragmentHandler fragmentHandler;
    private final PointerBytesStore pointerBytesStore = new PointerBytesStore();

    public AeronReceiver(Aeron aeron) {
        this.aeron = aeron;
    }

    @PostConstruct public void init() { start("core-aeron-receiver"); }

    @Override
    protected void onStart() {
        subscriber = new Subscriber(aeron, Channel.INBOUND, Channel.IN_STREAM);
        fragmentHandler = (buffer, offset, length, header) -> {
            pointerBytesStore.set(buffer.addressOffset() + offset, length);
            Storage.self().commandQueue().acquireAppender().writeDocument(wire -> {
                wire.write("msgType").int32(100); 
                wire.write("payload").bytes(pointerBytesStore);
                wire.write("aeronSeq").int64(header.position()); 
            });
        };
    }

    @Override protected int doWork() { return subscriber.poll(fragmentHandler, 10); }

    @Override protected void onStop() { if (subscriber != null) subscriber.close(); }
}
