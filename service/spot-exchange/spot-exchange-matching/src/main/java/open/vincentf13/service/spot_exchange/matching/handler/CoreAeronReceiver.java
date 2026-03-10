package open.vincentf13.service.spot_exchange.matching.handler;

import io.aeron.Aeron;
import io.aeron.logbuffer.FragmentHandler;
import jakarta.annotation.PostConstruct;
import open.vincentf13.service.spot_exchange.infra.transport.AeronSubscriber;
import open.vincentf13.service.spot_exchange.infra.worker.BusySpinWorker;
import open.vincentf13.service.spot_exchange.infra.store.StateStore;
import org.springframework.stereotype.Component;
import net.openhft.chronicle.bytes.PointerBytesStore;

import static open.vincentf13.service.spot_exchange.infra.constant.ExchangeConstants.INBOUND_CHANNEL;
import static open.vincentf13.service.spot_exchange.infra.constant.ExchangeConstants.INBOUND_STREAM_ID;

@Component
public class CoreAeronReceiver extends BusySpinWorker {
    private final Aeron aeron;
    private final StateStore stateStore;
    private AeronSubscriber subscriber;
    private FragmentHandler fragmentHandler;
    private final PointerBytesStore pointerBytesStore = new PointerBytesStore();

    public CoreAeronReceiver(Aeron aeron, StateStore stateStore) {
        this.aeron = aeron; this.stateStore = stateStore;
    }

    @PostConstruct public void init() { start("core-aeron-receiver"); }

    @Override
    protected void onStart() {
        subscriber = new AeronSubscriber(aeron, INBOUND_CHANNEL, INBOUND_STREAM_ID);
        fragmentHandler = (buffer, offset, length, header) -> {
            pointerBytesStore.set(buffer.addressOffset() + offset, length);
            stateStore.getCoreQueue().acquireAppender().writeDocument(wire -> {
                wire.write("msgType").int32(100); 
                wire.write("payload").bytes(pointerBytesStore);
                wire.write("aeronSeq").int64(header.position()); 
            });
        };
    }

    @Override protected int doWork() { return subscriber.poll(fragmentHandler, 10); }

    @Override protected void onStop() { if (subscriber != null) subscriber.close(); }
}
