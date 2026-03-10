package open.vincentf13.service.spot_exchange.gateway;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot_exchange.infra.*;
import open.vincentf13.service.spot_exchange.model.SystemProgress;

import static open.vincentf13.service.spot_exchange.infra.ExchangeConstants.*;

@Component
public class GatewayResultReceiver extends BusySpinWorker {
    private final Aeron aeron;
    private final StateStore stateStore;
    private Subscription subscription;
    private FragmentHandler fragmentHandler;
    private final SystemProgress progress = new SystemProgress();
    
    private final byte[] reusableArray = new byte[2048];
    private final net.openhft.chronicle.bytes.Bytes<?> writeBytes = net.openhft.chronicle.bytes.Bytes.wrapForRead(reusableArray);

    public GatewayResultReceiver(Aeron aeron, StateStore stateStore) {
        this.aeron = aeron;
        this.stateStore = stateStore;
    }

    @PostConstruct public void init() { start("gw-result-receiver"); }

    @Override
    protected void onStart() {
        subscription = aeron.addSubscription(OUTBOUND_CHANNEL, OUTBOUND_STREAM_ID);
        SystemProgress saved = stateStore.getSystemMetadataMap().get(PK_GW_OUTBOUND_SEQ);
        if (saved != null) progress.setLastProcessedSeq(saved.getLastProcessedSeq());
        else progress.setLastProcessedSeq(-1L);

        fragmentHandler = (buffer, offset, length, header) -> {
            long currentSeq = header.position();
            if (currentSeq <= progress.getLastProcessedSeq()) return;

            int len = Math.min(length, reusableArray.length);
            buffer.getBytes(offset, reusableArray, 0, len);
            writeBytes.readPositionRemaining(0, len);
            
            stateStore.getOutboundQueue().acquireAppender().writeDocument(wire -> {
                wire.bytes().write(writeBytes);
                wire.write("aeronSeq").int64(currentSeq);
            });
            
            progress.setLastProcessedSeq(currentSeq);
            stateStore.getSystemMetadataMap().put(PK_GW_OUTBOUND_SEQ, progress);
        };
    }

    @Override
    protected int doWork() {
        return subscription.poll(fragmentHandler, 10);
    }

    @Override
    protected void onStop() { if (subscription != null) subscription.close(); }
}
