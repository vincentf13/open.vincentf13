package open.vincentf13.service.spot.matching.aeron;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import jakarta.annotation.PostConstruct;
import open.vincentf13.service.spot.infra.Worker;
import open.vincentf13.service.spot.infra.chronicle.Storage;
import org.springframework.stereotype.Component;
import net.openhft.chronicle.bytes.PointerBytesStore;

import static open.vincentf13.service.spot.infra.Constants.*;

@Component
public class AeronReceiver extends Worker {
    private final Aeron aeron;
    private Subscription subscription;
    private FragmentHandler fragmentHandler;
    private final PointerBytesStore pointerBytesStore = new PointerBytesStore();

    public AeronReceiver(Aeron aeron) {
        this.aeron = aeron;
    }

    @PostConstruct public void init() { start("core-aeron-receiver"); }

    @Override
    protected void onStart() {
        subscription = aeron.addSubscription(Channel.INBOUND, Channel.IN_STREAM);
        fragmentHandler = (buffer, offset, length, header) -> {
            pointerBytesStore.set(buffer.addressOffset() + offset, length);
            Storage.self().commandQueue().acquireAppender().writeDocument(wire -> {
                wire.write(Fields.msgType).int32(100); 
                wire.write(Fields.payload).bytes(pointerBytesStore);
                wire.write(Fields.aeronSeq).int64(header.position()); 
            });
        };
    }

    @Override protected int doWork() { return subscription.poll(fragmentHandler, 10); }

    @Override protected void onStop() { if (subscription != null) subscription.close(); }
}
