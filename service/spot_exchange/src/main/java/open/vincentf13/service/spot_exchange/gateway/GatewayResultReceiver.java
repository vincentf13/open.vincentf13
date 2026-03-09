package open.vincentf13.service.spot_exchange.gateway;

import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** 
  Gateway 結果接收器 (Stream 30)
  負責訂閱來自 Core 的執行報告並寫入本地 outbound-queue
 */
@Component
public class GatewayResultReceiver implements Runnable {
    private final Aeron aeron;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ChronicleQueue outboundQueue;
    private Subscription subscription;
    private ExecutorService executor;

    public GatewayResultReceiver(Aeron aeron) {
        this.aeron = aeron;
    }

    @PostConstruct
    public void start() {
        outboundQueue = SingleChronicleQueueBuilder.binary("data/spot_exchange/outbound-queue").build();
        subscription = aeron.addSubscription("aeron:udp?endpoint=localhost:40445", 30);
        
        running.set(true);
        executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "gw-result-receiver"));
        executor.submit(this);
    }

    @Override
    public void run() {
        FragmentHandler fragmentHandler = (buffer, offset, length, header) -> {
            byte[] data = new byte[length];
            buffer.getBytes(offset, data);
            
            // 寫入本地隊列以便推送
            outboundQueue.acquireAppender().writeDocument(wire -> {
                wire.bytes().write(data);
            });
        };

        IdleStrategy idleStrategy = new BackoffIdleStrategy();
        while (running.get()) {
            int fragments = subscription.poll(fragmentHandler, 10);
            idleStrategy.idle(fragments);
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (executor != null) executor.shutdown();
        if (outboundQueue != null) outboundQueue.close();
    }
}
