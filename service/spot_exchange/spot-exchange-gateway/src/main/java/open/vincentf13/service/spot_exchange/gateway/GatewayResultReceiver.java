package open.vincentf13.service.spot_exchange.gateway;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import jakarta.annotation.PostConstruct;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.springframework.stereotype.Component;
import open.vincentf13.service.spot_exchange.infra.BusySpinWorker;
import open.vincentf13.service.spot_exchange.infra.StateStore;

@Component
public class GatewayResultReceiver extends BusySpinWorker {
    private final Aeron aeron;
    private final StateStore stateStore;
    private Subscription subscription;
    private FragmentHandler fragmentHandler;
    private long lastReceivedSeq = -1;

    public GatewayResultReceiver(Aeron aeron, StateStore stateStore) {
        this.aeron = aeron;
        this.stateStore = stateStore;
    }

    @PostConstruct
    public void init() { start("gw-result-receiver"); }

    @Override
    protected void onStart() {
        subscription = aeron.addSubscription("aeron:udp?endpoint=localhost:40445", 30);
        
        // 恢復上次處理進度 (從系統狀態讀取)
        this.lastReceivedSeq = stateStore.getSystemStateMap().getOrDefault("lastGwResultSeq", -1L);

        fragmentHandler = (buffer, offset, length, header) -> {
            long currentSeq = header.position();
            // --- 深度優化：網關回報冪等落地 ---
            if (currentSeq <= lastReceivedSeq) return;

            byte[] data = new byte[length];
            buffer.getBytes(offset, data);
            
            stateStore.getOutboundQueue().acquireAppender().writeDocument(wire -> {
                wire.bytes().write(data);
                wire.write("aeronSeq").int64(currentSeq);
            });
            
            lastReceivedSeq = currentSeq;
            stateStore.getSystemStateMap().put("lastGwResultSeq", currentSeq);
        };
    }

    @Override
    protected int doWork() {
        return subscription.poll(fragmentHandler, 10);
    }

    @Override
    protected void onStop() {
        if (subscription != null) subscription.close();
    }
}
